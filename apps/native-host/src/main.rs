//! Vazie VPN helper.
//!
//! A browser extension cannot open a tunnel by itself: it cannot listen on a socket or run a VPN engine. This
//! helper is the small native half. The browser starts it over Chrome's native messaging (`app.vazie.vpn`); it
//! runs the user's own configuration in Xray, bound to a loopback-only proxy, and the
//! extension points the browser at that proxy. Run by hand it installs itself (`install`).

mod engine;
mod install;
mod paths;
mod probe;
mod proto;
mod session;

use serde_json::{Value, json};
use session::SharedHandle;
use std::io::{self, Write};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

const VERSION: &str = env!("CARGO_PKG_VERSION");

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    // Chromium starts a native host with the caller's origin as its first argument.
    if args.first().is_some_and(|a| a.starts_with("chrome-extension://")) {
        native();
        return;
    }
    std::process::exit(cli(&args));
}

// ── native messaging ──────────────────────────────────────────────────────────────────────────────

#[derive(Clone)]
struct Out(Arc<Mutex<io::Stdout>>);

impl Out {
    fn send(&self, message: Value) {
        if let Ok(mut stdout) = self.0.lock() {
            let _ = proto::write_frame(&mut *stdout, &message);
        }
    }
}

fn native() {
    let out = Out(Arc::new(Mutex::new(io::stdout())));
    let shared: SharedHandle = Arc::new(session::Shared::default());

    // An engine that dies by itself is reported once; nothing else watches it.
    {
        let (out, shared) = (out.clone(), Arc::clone(&shared));
        thread::spawn(move || loop {
            thread::sleep(Duration::from_millis(500));
            if let Some(code) = shared.poll_exit() {
                out.send(json!({ "event": "exited", "code": code }));
            }
        });
    }

    let mut stdin = io::stdin().lock();
    // The browser going away (a closed pipe or a broken one) ends the loop, and the tunnel with it.
    while let Ok(Some(frame)) = proto::read_frame(&mut stdin) {
        let Ok(request) = serde_json::from_slice::<Value>(&frame) else { continue };
        let (out, shared) = (out.clone(), Arc::clone(&shared));
        thread::spawn(move || {
            let id = request.get("id").cloned().unwrap_or(Value::Null);
            let mut reply = handle(&request, &shared, &out);
            if let Value::Object(map) = &mut reply {
                map.insert("id".into(), id);
            }
            out.send(reply);
        });
    }
    shared.stop();
}

fn fail(code: &str) -> Value {
    json!({ "ok": false, "code": code })
}

fn handle(request: &Value, shared: &SharedHandle, out: &Out) -> Value {
    let kind = request.get("type").and_then(Value::as_str).unwrap_or_default();
    match kind {
        "hello" => {
            let status = engine::status();
            json!({
                "ok": true,
                "version": VERSION,
                "platform": engine::platform(),
                "engine": {
                    "installed": status.installed,
                    "supported": status.supported,
                    "version": if status.installed { engine::running_version() } else { None },
                    "pinned": status.version,
                }
            })
        }
        "engine.install" => {
            let progress_out = out.clone();
            match engine::install(move |done, total| {
                progress_out.send(json!({ "event": "engineProgress", "done": done, "total": total }));
            }) {
                Ok(()) => json!({ "ok": true }),
                Err(error) => fail(error.code()),
            }
        }
        "start" => {
            let outbounds = request.get("outbounds").cloned().unwrap_or(Value::Null);
            match shared.start(outbounds) {
                Ok((port, _)) => json!({ "ok": true, "port": port }),
                Err(error) => fail(error.code()),
            }
        }
        "ready" => match shared.proxy_port() {
            None => fail("not_running"),
            Some(port) => match probe::readiness(port) {
                Ok(()) => json!({ "ok": true }),
                Err(stage) => json!({ "ok": false, "code": "not_ready", "stage": stage.code() }),
            },
        },
        "traffic" => match shared.traffic() {
            Some((rx, tx)) => json!({ "ok": true, "rx": rx, "tx": tx }),
            None => fail("unavailable"),
        },
        "stop" => {
            shared.stop();
            json!({ "ok": true })
        }
        "probe" => {
            let host = request.get("host").and_then(Value::as_str).unwrap_or_default();
            let port = request.get("port").and_then(Value::as_u64).and_then(|p| u16::try_from(p).ok());
            match port {
                Some(port) if !host.is_empty() && host.len() <= 253 => {
                    json!({ "ok": true, "ms": probe::latency(host, port) })
                }
                _ => fail("request"),
            }
        }
        _ => fail("unknown_request"),
    }
}

// ── command line ──────────────────────────────────────────────────────────────────────────────────

/// English or Russian, by the person's own locale: this is what an ordinary person sees when they double-click it.
fn say(english: &str, russian: &str) -> String {
    let locale = ["LC_ALL", "LC_MESSAGES", "LANG"]
        .iter()
        .find_map(|name| std::env::var(name).ok().filter(|v| !v.is_empty()))
        .unwrap_or_default()
        .to_lowercase();
    if locale.starts_with("ru") { russian.to_owned() } else { english.to_owned() }
}

fn cli(args: &[String]) -> i32 {
    match args.first().map(String::as_str) {
        // Opened with a double-click: set everything up, say so plainly, and keep the window until it is read.
        None => {
            let code = cmd_install(&[]);
            pause_for_reading();
            code
        }
        Some("install") => cmd_install(&args[1..]),
        Some("uninstall") => cmd_uninstall(&args[1..]),
        Some("engine") => cmd_engine(),
        Some("status") => cmd_status(),
        Some("--version") | Some("version") => {
            println!("vazie-vpn-host {VERSION} (engine {})", engine::VERSION);
            0
        }
        _ => {
            eprintln!(
                "Vazie VPN helper {VERSION}\n\n\
                 Usage:\n  vazie-vpn-host [install] [--extension-id ID]... [--all-browsers] [--skip-engine] [--quiet]\n  \
                 vazie-vpn-host uninstall [--keep-engine]\n  vazie-vpn-host engine\n  vazie-vpn-host status\n\n\
                 With no arguments it installs: it finds the Vazie VPN extension in your browsers, registers itself\n\
                 and downloads the engine. The browser starts this program itself; you only run it to install or remove it."
            );
            2
        }
    }
}

fn pause_for_reading() {
    use std::io::IsTerminal;
    if io::stdin().is_terminal() {
        println!("\n{}", say("Press Enter to close this window.", "Нажмите Enter, чтобы закрыть окно."));
        let mut line = String::new();
        let _ = io::stdin().read_line(&mut line);
    }
}

fn cmd_install(args: &[String]) -> i32 {
    let mut ids: Vec<String> = install::DEFAULT_EXTENSION_IDS.iter().map(|s| s.to_string()).collect();
    if let Ok(extra) = std::env::var("VAZIE_EXTENSION_IDS") {
        ids.extend(extra.split(',').map(|s| s.trim().to_owned()).filter(|s| !s.is_empty()));
    }
    let (mut all, mut skip_engine, mut quiet) = (false, false, false);
    let mut iter = args.iter();
    while let Some(arg) = iter.next() {
        match arg.as_str() {
            "--extension-id" => match iter.next() {
                Some(id) => ids.push(id.clone()),
                None => {
                    eprintln!("--extension-id needs a value");
                    return 2;
                }
            },
            "--all-browsers" => all = true,
            "--skip-engine" => skip_engine = true,
            "--quiet" => quiet = true,
            other => {
                eprintln!("unknown option {other}");
                return 2;
            }
        }
    }

    // The extension is looked for where the browsers keep it, so nobody has to know an id.
    let discovered = install::discover_extension_ids();
    for (id, browser) in &discovered {
        if !quiet {
            println!("{}", say(&format!("Found Vazie VPN in {browser}."), &format!("Vazie VPN найден в {browser}.")));
        }
        ids.push(id.clone());
    }
    ids.retain(|id| install::is_extension_id(id));
    ids.sort();
    ids.dedup();
    if ids.is_empty() {
        eprintln!("{}", say(
            "Vazie VPN was not found in any browser. Install the extension first, then run this again.",
            "Расширение Vazie VPN не найдено ни в одном браузере. Сначала установите расширение, затем запустите это снова.",
        ));
        return 1;
    }

    let host = match install::place_binary() {
        Ok(path) => path,
        Err(error) => {
            eprintln!("{}", say(
                &format!("Could not place the helper in {}: {error}", paths::bin_dir().display()),
                &format!("Не удалось установить помощник в {}: {error}", paths::bin_dir().display()),
            ));
            return 1;
        }
    };
    // Registered for the browsers it was found in; with none found (a store build not yet opened), every installed one.
    let browsers = install::register(&host, &ids, all);
    if browsers.is_empty() {
        eprintln!("{}", say(
            "No supported browser found. Chrome, Edge, Brave, Vivaldi, Chromium and Arc are supported.",
            "Поддерживаемый браузер не найден. Поддерживаются Chrome, Edge, Brave, Vivaldi, Chromium и Arc.",
        ));
        return 1;
    }
    if !quiet {
        println!("{}", say(&format!("Helper installed: {}", host.display()), &format!("Помощник установлен: {}", host.display())));
        println!("{}", say(&format!("Registered for: {}", browsers.join(", ")), &format!("Зарегистрирован в: {}", browsers.join(", "))));
    }
    if skip_engine {
        return 0;
    }
    let code = cmd_engine();
    if code == 0 {
        println!("\n{}", say(
            "Done. Go back to Vazie VPN in your browser — it will notice the helper by itself.",
            "Готово. Вернитесь в Vazie VPN в браузере — он сам заметит помощника.",
        ));
    }
    code
}

fn cmd_engine() -> i32 {
    let status = engine::status();
    if !status.supported {
        eprintln!("The Xray engine has no build pinned for {}.", engine::platform());
        return 1;
    }
    if status.installed {
        println!("{}", say(&format!("Engine: Xray {} is installed.", engine::VERSION), &format!("Движок: Xray {} установлен.", engine::VERSION)));
        return 0;
    }
    println!("{}", say(
        &format!("Downloading the engine (Xray {}) and checking it …", engine::VERSION),
        &format!("Скачиваем движок (Xray {}) и проверяем его …", engine::VERSION),
    ));
    let mut last = 0u64;
    let result = engine::install(|done, total| {
        let percent = (done * 100).checked_div(total).unwrap_or(0);
        if percent != last {
            last = percent;
            eprint!("\r  {percent}%");
            let _ = io::stderr().flush();
        }
    });
    eprintln!();
    match result {
        Ok(()) => {
            println!("{}", say("Engine installed.", "Движок установлен."));
            0
        }
        Err(error) => {
            eprintln!("{}", say(
                &format!("The engine could not be installed ({}). Check your internet connection and run this again.", error.code()),
                &format!("Не удалось установить движок ({}). Проверьте интернет и запустите это снова.", error.code()),
            ));
            1
        }
    }
}

fn cmd_uninstall(args: &[String]) -> i32 {
    let removed = install::unregister();
    println!("Unregistered from: {}", if removed.is_empty() { "nothing".to_owned() } else { removed.join(", ") });
    let keep = args.iter().any(|a| a == "--keep-engine");
    if !keep {
        let _ = std::fs::remove_dir_all(paths::data_dir().join("engine"));
        println!("Engine removed.");
    }
    let _ = std::fs::remove_file(paths::installed_host_path());
    0
}

fn cmd_status() -> i32 {
    let status = engine::status();
    println!("Helper {VERSION} at {}", paths::installed_host_path().display());
    println!("Platform {}", engine::platform());
    println!(
        "Engine Xray {}: {}",
        engine::VERSION,
        if status.installed { "installed" } else if status.supported { "not installed" } else { "unsupported platform" }
    );
    for (id, browser) in install::discover_extension_ids() {
        println!("Extension {id} in {browser}");
    }
    0
}
