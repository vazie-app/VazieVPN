//! One running Xray: started from a configuration the helper composes itself, stopped on request, when the browser
//! goes away, or when the engine dies on its own.
//!
//! The browser sends only the outbounds. The inbounds — a loopback HTTP proxy and the stats API — are written
//! here, because this is the component that opens sockets and so the one that decides they are bound to the
//! loopback address and never reachable from outside the machine. (A proxy login was tried and dropped: Chrome
//! answers a proxy's login challenge only for an extension that holds access to every site, and a VPN extension
//! should not ask for that.)

use crate::paths;
use crate::engine;
use serde_json::{Value, json};
use std::io::{Read, Write};
use std::net::{Ipv4Addr, SocketAddr, TcpListener, TcpStream};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

pub struct Running {
    child: Child,
    pub http_port: u16,
    pub api_port: u16,
}

#[derive(Default)]
pub struct Shared {
    running: Mutex<Option<Running>>,
}

pub type SharedHandle = Arc<Shared>;

#[derive(Debug, PartialEq, Eq)]
pub enum StartError {
    /// The helper has no engine to run.
    Engine,
    /// The request was not something the helper will run.
    Request,
    /// The engine did not accept the configuration.
    Config,
    /// A loopback port could not be bound.
    Bind,
    /// The engine did not come up in time.
    Timeout,
}

impl StartError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Engine => "engine",
            Self::Request => "request",
            Self::Config => "config",
            Self::Bind => "bind",
            Self::Timeout => "timeout",
        }
    }
}

#[cfg(windows)]
pub fn hide_window(command: &mut Command) {
    use std::os::windows::process::CommandExt;
    command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
}

#[cfg(not(windows))]
pub fn hide_window(_command: &mut Command) {}

fn free_port() -> Option<u16> {
    let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).ok()?;
    Some(listener.local_addr().ok()?.port())
}

/// The outbounds a browser may ask for: the user's VLESS server and the blackhole, by their own tags, and nothing
/// that would let a request turn the helper into a way onto the open internet.
fn outbounds_allowed(outbounds: &Value) -> bool {
    let Some(list) = outbounds.as_array() else { return false };
    if list.is_empty() || list.len() > 4 {
        return false;
    }
    list.iter().all(|outbound| {
        let tag = outbound.get("tag").and_then(Value::as_str);
        let protocol = outbound.get("protocol").and_then(Value::as_str);
        let known = matches!((tag, protocol), (Some("proxy"), Some("vless")) | (Some("block"), Some("blackhole")));
        known && keys_allowed(outbound)
    })
}

/// The keys the extension writes into an outbound, and no others: `proxySettings`, `sendThrough`, `mux` and the like
/// are not part of what it asks for, and `sockopt` (an interface, a firewall mark, a dialer chain) would let a request
/// steer the engine's own sockets.
fn keys_allowed(outbound: &Value) -> bool {
    let Some(map) = outbound.as_object() else { return false };
    if !map.keys().all(|key| matches!(key.as_str(), "tag" | "protocol" | "settings" | "streamSettings")) {
        return false;
    }
    match map.get("streamSettings") {
        None => true,
        Some(stream) => stream.as_object().is_some_and(|stream| !stream.contains_key("sockopt")),
    }
}

/// The processes listening on `port`, when the system can say (`lsof` on macOS and Linux, `netstat` on Windows);
/// `None` when it cannot, which is not taken to mean that somebody else holds the port.
fn listeners(port: u16) -> Option<Vec<u32>> {
    #[cfg(windows)]
    {
        let mut command = Command::new("netstat");
        command.args(["-ano", "-p", "TCP"]).stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::null());
        hide_window(&mut command);
        let output = run_with_timeout(command, Duration::from_secs(5))?;
        let suffix = format!("127.0.0.1:{port}");
        let pids = String::from_utf8_lossy(&output)
            .lines()
            .filter(|line| line.contains("LISTENING"))
            .filter_map(|line| {
                let columns: Vec<&str> = line.split_whitespace().collect();
                (columns.get(1) == Some(&suffix.as_str())).then(|| columns.last()?.parse::<u32>().ok()).flatten()
            })
            .collect();
        Some(pids)
    }
    #[cfg(not(windows))]
    {
        let tool = if cfg!(target_os = "macos") { "/usr/sbin/lsof" } else { "lsof" };
        let mut command = Command::new(tool);
        command
            .args(["-nP", &format!("-iTCP:{port}"), "-sTCP:LISTEN", "-Fp"])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        let output = run_with_timeout(command, Duration::from_secs(5))?;
        let pids = String::from_utf8_lossy(&output)
            .lines()
            .filter_map(|line| line.strip_prefix('p')?.parse::<u32>().ok())
            .collect();
        Some(pids)
    }
}

/// Whether every listener the system reports on `port` is `pid`. Ports are chosen, released and handed to the engine,
/// so another program could have bound one first; the browser would then send its traffic to that program.
fn port_is_ours(port: u16, pid: u32) -> bool {
    match listeners(port) {
        Some(found) if !found.is_empty() => found.iter().all(|&other| other == pid),
        _ => true,
    }
}

pub fn compose_config(outbounds: Value, http_port: u16, api_port: u16) -> Value {
    json!({
        "log": { "loglevel": "none" },
        "stats": {},
        "api": { "tag": "api", "services": ["StatsService"] },
        "policy": { "system": { "statsOutboundUplink": true, "statsOutboundDownlink": true } },
        "inbounds": [
            {
                "tag": "http-in",
                "listen": "127.0.0.1",
                "port": http_port,
                "protocol": "http",
                "settings": { "allowTransparent": false, "userLevel": 0 }
            },
            {
                "tag": "api",
                "listen": "127.0.0.1",
                "port": api_port,
                "protocol": "dokodemo-door",
                "settings": { "address": "127.0.0.1" }
            }
        ],
        "outbounds": outbounds,
        "routing": {
            "domainStrategy": "AsIs",
            "rules": [
                { "type": "field", "inboundTag": ["api"], "outboundTag": "api" },
                { "type": "field", "inboundTag": ["http-in"], "outboundTag": "proxy" }
            ]
        }
    })
}

impl Shared {
    pub fn start(&self, outbounds: Value) -> Result<(u16, u16), StartError> {
        self.stop();
        if !outbounds_allowed(&outbounds) {
            return Err(StartError::Request);
        }
        let binary = paths::engine_binary(engine::VERSION);
        if !engine::verify_binary() {
            return Err(StartError::Engine);
        }
        let (http_port, api_port) = match (free_port(), free_port()) {
            (Some(a), Some(b)) if a != b => (a, b),
            _ => return Err(StartError::Bind),
        };
        let config = compose_config(outbounds, http_port, api_port);

        let mut command = Command::new(binary);
        command
            .args(["run", "-config", "stdin:"])
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::piped());
        hide_window(&mut command);
        let mut child = command.spawn().map_err(|_| StartError::Engine)?;

        // The configuration goes down a pipe: the user's keys never touch the disk.
        if let Some(mut stdin) = child.stdin.take()
            && stdin.write_all(config.to_string().as_bytes()).is_err() {
                let _ = child.kill();
                return Err(StartError::Config);
            }
        let stderr_text = Arc::new(Mutex::new(String::new()));
        if let Some(mut stderr) = child.stderr.take() {
            let sink = Arc::clone(&stderr_text);
            thread::spawn(move || {
                let mut buffer = [0u8; 1024];
                while let Ok(read) = stderr.read(&mut buffer) {
                    if read == 0 {
                        break;
                    }
                    if let Ok(mut text) = sink.lock()
                        && text.len() < 4096 {
                            text.push_str(&String::from_utf8_lossy(&buffer[..read]));
                        }
                }
            });
        }

        let deadline = Instant::now() + Duration::from_secs(8);
        loop {
            if let Ok(Some(_)) = child.try_wait() {
                // Whatever the engine printed may quote the configuration, so it is classified here and
                // never forwarded.
                thread::sleep(Duration::from_millis(50));
                let text = stderr_text.lock().map(|t| t.to_lowercase()).unwrap_or_default();
                let bind = text.contains("address already in use") || text.contains("only one usage");
                return Err(if bind { StartError::Bind } else { StartError::Config });
            }
            let address = SocketAddr::from((Ipv4Addr::LOCALHOST, http_port));
            if TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok() {
                // Somebody answered; make sure it is the engine that was just started.
                if !(port_is_ours(http_port, child.id()) && port_is_ours(api_port, child.id())) {
                    let _ = child.kill();
                    let _ = child.wait();
                    return Err(StartError::Bind);
                }
                break;
            }
            if Instant::now() >= deadline {
                let _ = child.kill();
                let _ = child.wait();
                return Err(StartError::Timeout);
            }
            thread::sleep(Duration::from_millis(60));
        }

        *self.running.lock().expect("session lock") =
            Some(Running { child, http_port, api_port });
        Ok((http_port, api_port))
    }

    pub fn stop(&self) {
        if let Some(mut running) = self.running.lock().expect("session lock").take() {
            let _ = running.child.kill();
            let _ = running.child.wait();
        }
    }

    /// The exit status of an engine that has ended by itself, once; `None` while it runs or is not started.
    pub fn poll_exit(&self) -> Option<i32> {
        let mut guard = self.running.lock().expect("session lock");
        let status = guard.as_mut()?.child.try_wait().ok()??;
        *guard = None;
        Some(status.code().unwrap_or(-1))
    }

    /// The port the readiness probe goes through.
    pub fn proxy_port(&self) -> Option<u16> {
        self.running.lock().expect("session lock").as_ref().map(|r| r.http_port)
    }

    /// Bytes through the tunnel so far: `(downloaded, uploaded)`, from the engine's own counters.
    pub fn traffic(&self) -> Option<(u64, u64)> {
        let api_port = self.running.lock().expect("session lock").as_ref()?.api_port;
        let mut command = Command::new(paths::engine_binary(engine::VERSION));
        command
            .args(["api", "statsquery", &format!("--server=127.0.0.1:{api_port}"), "-pattern", "outbound>>>proxy>>>traffic"])
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        hide_window(&mut command);
        let output = run_with_timeout(command, Duration::from_secs(3))?;
        parse_traffic(&output)
    }
}

/// Runs a short command and gives up on it after `limit`.
fn run_with_timeout(mut command: Command, limit: Duration) -> Option<Vec<u8>> {
    let mut child = command.spawn().ok()?;
    let mut stdout = child.stdout.take()?;
    let reader = thread::spawn(move || {
        let mut bytes = Vec::new();
        let _ = stdout.read_to_end(&mut bytes);
        bytes
    });
    let deadline = Instant::now() + limit;
    loop {
        match child.try_wait() {
            Ok(Some(status)) if status.success() => return reader.join().ok(),
            Ok(Some(_)) | Err(_) => return None,
            Ok(None) if Instant::now() >= deadline => {
                let _ = child.kill();
                let _ = child.wait();
                return None;
            }
            Ok(None) => thread::sleep(Duration::from_millis(20)),
        }
    }
}

/// `{"stat":[{"name":"outbound>>>proxy>>>traffic>>>downlink","value":"123"}, …]}`; a counter that has not moved yet
/// is simply absent.
pub fn parse_traffic(output: &[u8]) -> Option<(u64, u64)> {
    let document: Value = serde_json::from_slice(output).ok()?;
    let mut down = 0u64;
    let mut up = 0u64;
    if let Some(stats) = document.get("stat").and_then(Value::as_array) {
        for stat in stats {
            let name = stat.get("name").and_then(Value::as_str).unwrap_or_default();
            let value = match stat.get("value") {
                Some(Value::String(text)) => text.parse::<u64>().unwrap_or(0),
                Some(Value::Number(number)) => number.as_u64().unwrap_or(0),
                _ => 0,
            };
            if name.ends_with(">>>downlink") {
                down = value;
            } else if name.ends_with(">>>uplink") {
                up = value;
            }
        }
    }
    Some((down, up))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_the_servers_own_outbounds_are_run() {
        let ok = json!([{"tag": "proxy", "protocol": "vless"}, {"tag": "block", "protocol": "blackhole"}]);
        assert!(outbounds_allowed(&ok));
        assert!(!outbounds_allowed(&json!([{"tag": "direct", "protocol": "freedom"}])));
        assert!(!outbounds_allowed(&json!([{"tag": "proxy", "protocol": "freedom"}])));
        assert!(!outbounds_allowed(&json!([])));
        assert!(!outbounds_allowed(&json!("proxy")));
    }

    #[test]
    fn an_outbound_may_carry_only_the_keys_the_extension_writes() {
        let plain = json!([{"tag": "proxy", "protocol": "vless", "settings": {}, "streamSettings": {"network": "raw"}}]);
        assert!(outbounds_allowed(&plain));
        for extra in [json!({"proxySettings": {"tag": "x"}}), json!({"sendThrough": "10.0.0.1"}), json!({"mux": {}})] {
            let mut outbound = json!({"tag": "proxy", "protocol": "vless"});
            outbound.as_object_mut().unwrap().extend(extra.as_object().unwrap().clone());
            assert!(!outbounds_allowed(&json!([outbound])));
        }
        let sockopt = json!([{"tag": "proxy", "protocol": "vless", "streamSettings": {"sockopt": {"dialerProxy": "x"}}}]);
        assert!(!outbounds_allowed(&sockopt));
    }

    #[test]
    fn a_port_held_by_another_process_is_not_ours() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let port = listener.local_addr().unwrap().port();
        assert!(port_is_ours(port, std::process::id()));
        assert!(!port_is_ours(port, std::process::id().wrapping_add(1)));
    }

    #[test]
    fn the_inbounds_are_loopback_only() {
        let config = compose_config(json!([]), 1080, 1081);
        for inbound in config["inbounds"].as_array().unwrap() {
            assert_eq!(inbound["listen"], "127.0.0.1");
        }
        assert_eq!(config["log"]["loglevel"], "none");
    }

    #[test]
    fn traffic_is_read_from_both_counters() {
        let text = br#"{"stat":[{"name":"outbound>>>proxy>>>traffic>>>uplink","value":"10"},{"name":"outbound>>>proxy>>>traffic>>>downlink","value":"2048"}]}"#;
        assert_eq!(parse_traffic(text), Some((2048, 10)));
        assert_eq!(parse_traffic(b"{}"), Some((0, 0)));
        assert_eq!(parse_traffic(b"nonsense"), None);
    }

    #[test]
    fn a_request_for_a_direct_outbound_is_refused() {
        let shared = Shared::default();
        let outbounds = json!([{"tag": "direct", "protocol": "freedom"}]);
        assert_eq!(shared.start(outbounds).unwrap_err(), StartError::Request);
    }
}
