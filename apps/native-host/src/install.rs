//! Registering the helper with the browsers: a small JSON manifest per browser that names this binary and the
//! extension allowed to start it. Only the listed extension ids can ever talk to the helper.

use crate::paths::{self, HOST_NAME};
use serde_json::json;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

/// The id the extension has when it is loaded from the repository (it is derived from the `key` in its manifest).
/// A store build has its own id; pass it with `--extension-id`.
pub const DEFAULT_EXTENSION_IDS: &[&str] = &["apmoogcaoomcoaomkacmlngnknmgieeg"];

pub struct Browser {
    pub name: &'static str,
    /// Where the browser looks for manifests (macOS and Linux); on Windows, the registry key under HKCU.
    manifest_location: PathBuf,
    /// Exists when the browser has ever run for this person.
    pub profile_root: PathBuf,
}

#[cfg(target_os = "macos")]
pub fn browsers() -> Vec<Browser> {
    let support = paths::home().join("Library/Application Support");
    let entry = |name: &'static str, root: &str| Browser {
        name,
        manifest_location: support.join(root).join("NativeMessagingHosts"),
        profile_root: support.join(root),
    };
    vec![
        entry("Google Chrome", "Google/Chrome"),
        entry("Chrome Beta", "Google/Chrome Beta"),
        entry("Chromium", "Chromium"),
        entry("Microsoft Edge", "Microsoft Edge"),
        entry("Brave", "BraveSoftware/Brave-Browser"),
        entry("Vivaldi", "Vivaldi"),
        entry("Arc", "Arc/User Data"),
        entry("Dia", "Dia/User Data"),
        entry("Chrome Canary", "Google/Chrome Canary"),
        entry("Opera", "com.operasoftware.Opera"),
        entry("Yandex Browser", "Yandex/YandexBrowser"),
    ]
}

#[cfg(all(unix, not(target_os = "macos")))]
pub fn browsers() -> Vec<Browser> {
    let config = std::env::var_os("XDG_CONFIG_HOME")
        .map(PathBuf::from)
        .filter(|p| p.is_absolute())
        .unwrap_or_else(|| paths::home().join(".config"));
    let entry = |name: &'static str, root: &str| Browser {
        name,
        manifest_location: config.join(root).join("NativeMessagingHosts"),
        profile_root: config.join(root),
    };
    vec![
        entry("Google Chrome", "google-chrome"),
        entry("Chrome Beta", "google-chrome-beta"),
        entry("Chromium", "chromium"),
        entry("Microsoft Edge", "microsoft-edge"),
        entry("Brave", "BraveSoftware/Brave-Browser"),
        entry("Vivaldi", "vivaldi"),
        entry("Opera", "opera"),
        entry("Yandex Browser", "yandex-browser"),
    ]
}

#[cfg(windows)]
pub fn browsers() -> Vec<Browser> {
    let local = std::env::var_os("LOCALAPPDATA").map(PathBuf::from).unwrap_or_else(paths::home);
    let entry = |name: &'static str, key: &str, root: &str| Browser {
        name,
        manifest_location: PathBuf::from(key),
        profile_root: local.join(root),
    };
    vec![
        entry("Google Chrome", r"Software\Google\Chrome\NativeMessagingHosts", r"Google\Chrome"),
        entry("Chromium", r"Software\Chromium\NativeMessagingHosts", "Chromium"),
        entry("Microsoft Edge", r"Software\Microsoft\Edge\NativeMessagingHosts", r"Microsoft\Edge"),
        entry("Brave", r"Software\BraveSoftware\Brave-Browser\NativeMessagingHosts", r"BraveSoftware\Brave-Browser"),
        entry("Vivaldi", r"Software\Vivaldi\NativeMessagingHosts", "Vivaldi"),
    ]
}

pub fn manifest_json(host_path: &Path, extension_ids: &[String]) -> serde_json::Value {
    json!({
        "name": HOST_NAME,
        "description": "Vazie VPN helper: runs your own Xray tunnel for the Vazie VPN browser extension",
        "path": host_path,
        "type": "stdio",
        "allowed_origins": extension_ids.iter().map(|id| format!("chrome-extension://{id}/")).collect::<Vec<_>>(),
    })
}

/// A Chromium extension id: 32 letters from `a` to `p`.
pub fn is_extension_id(id: &str) -> bool {
    id.len() == 32 && id.bytes().all(|b| (b'a'..=b'p').contains(&b))
}

/// Copies this binary to its permanent place, so the manifests keep pointing at something that stays.
pub fn place_binary() -> io::Result<PathBuf> {
    let target = paths::installed_host_path();
    fs::create_dir_all(target.parent().expect("a directory"))?;
    let current = std::env::current_exe()?;
    if current != target {
        let partial = target.with_extension("part");
        fs::copy(&current, &partial)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&partial, fs::Permissions::from_mode(0o755))?;
        }
        fs::rename(&partial, &target)?;
    }
    Ok(target)
}

/// Registers the helper with every installed browser (or every known one). Returns the browsers it registered with.
pub fn register(host_path: &Path, extension_ids: &[String], all: bool) -> Vec<&'static str> {
    let manifest = serde_json::to_vec_pretty(&manifest_json(host_path, extension_ids)).expect("json");
    let mut registered = Vec::new();
    for browser in browsers() {
        if !all && !browser.profile_root.exists() {
            continue;
        }
        if write_manifest(&browser, &manifest).is_ok() {
            registered.push(browser.name);
        }
    }
    registered
}

pub fn unregister() -> Vec<&'static str> {
    let mut removed = Vec::new();
    for browser in browsers() {
        if remove_manifest(&browser) {
            removed.push(browser.name);
        }
    }
    removed
}

#[cfg(not(windows))]
fn write_manifest(browser: &Browser, manifest: &[u8]) -> io::Result<()> {
    fs::create_dir_all(&browser.manifest_location)?;
    fs::write(browser.manifest_location.join(format!("{HOST_NAME}.json")), manifest)
}

#[cfg(not(windows))]
fn remove_manifest(browser: &Browser) -> bool {
    fs::remove_file(browser.manifest_location.join(format!("{HOST_NAME}.json"))).is_ok()
}

// On Windows the manifest is one file and each browser points at it from the registry.
#[cfg(windows)]
fn manifest_file() -> PathBuf {
    paths::data_dir().join(format!("{HOST_NAME}.json"))
}

#[cfg(windows)]
fn registry_key(browser: &Browser) -> String {
    format!(r"HKCU\{}\{HOST_NAME}", browser.manifest_location.display())
}

#[cfg(windows)]
fn write_manifest(browser: &Browser, manifest: &[u8]) -> io::Result<()> {
    use std::process::Command;
    fs::create_dir_all(paths::data_dir())?;
    fs::write(manifest_file(), manifest)?;
    let status = Command::new("reg")
        .args(["add", &registry_key(browser), "/ve", "/t", "REG_SZ", "/d"])
        .arg(manifest_file())
        .arg("/f")
        .output()?
        .status;
    if status.success() { Ok(()) } else { Err(io::Error::other("reg add failed")) }
}

#[cfg(windows)]
fn remove_manifest(browser: &Browser) -> bool {
    use std::process::Command;
    let removed = Command::new("reg")
        .args(["delete", &registry_key(browser), "/f"])
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false);
    let _ = fs::remove_file(manifest_file());
    removed
}

/// An extension that is Vazie VPN: it can start the helper, it can drive the proxy, and it has the toggle command.
/// Names are not enough (a store build and an unpacked build are both "Vazie VPN", but so is anything that says so).
fn is_vazie_manifest(manifest: &serde_json::Value) -> bool {
    let permissions: Vec<&str> = manifest
        .get("permissions")
        .and_then(|p| p.as_array())
        .map(|list| list.iter().filter_map(|v| v.as_str()).collect())
        .unwrap_or_default();
    permissions.contains(&"nativeMessaging")
        && permissions.contains(&"proxy")
        && manifest.get("commands").and_then(|c| c.get("toggle-connection")).is_some()
}

/// The ids of the Vazie VPN extensions this person has installed, in any profile of any browser, found the way the
/// browser itself records them: each profile's preferences name every extension and where it is. This is what lets
/// an ordinary person run the installer without ever typing an id — the store build, an unpacked build and a
/// copy with another id are all found.
pub fn discover_extension_ids() -> Vec<(String, String)> {
    let mut found: Vec<(String, String)> = Vec::new();
    for browser in browsers() {
        let Ok(profiles) = fs::read_dir(&browser.profile_root) else { continue };
        for profile in profiles.flatten() {
            let directory = profile.path();
            if !directory.join("Preferences").exists() && !directory.join("Secure Preferences").exists() {
                continue;
            }
            for file in ["Secure Preferences", "Preferences"] {
                let Ok(text) = fs::read_to_string(directory.join(file)) else { continue };
                let Ok(document) = serde_json::from_str::<serde_json::Value>(&text) else { continue };
                let Some(settings) = document.pointer("/extensions/settings").and_then(|s| s.as_object()) else { continue };
                for (id, entry) in settings {
                    if !is_extension_id(id) || found.iter().any(|(known, _)| known == id) {
                        continue;
                    }
                    let Some(path) = entry.get("path").and_then(|p| p.as_str()) else { continue };
                    // A store install records a path relative to the profile's Extensions directory.
                    let base = if Path::new(path).is_absolute() { PathBuf::from(path) } else { directory.join("Extensions").join(path) };
                    let Ok(manifest) = fs::read_to_string(base.join("manifest.json")) else { continue };
                    let Ok(manifest) = serde_json::from_str::<serde_json::Value>(&manifest) else { continue };
                    if is_vazie_manifest(&manifest) {
                        found.push((id.clone(), browser.name.to_string()));
                    }
                }
            }
        }
    }
    found
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_manifest_names_only_the_given_extensions() {
        let ids = vec!["a".repeat(32), "p".repeat(32)];
        let manifest = manifest_json(Path::new("/x/vazie-vpn-host"), &ids);
        assert_eq!(manifest["name"], "app.vazie.vpn");
        assert_eq!(manifest["type"], "stdio");
        let origins: Vec<_> = manifest["allowed_origins"].as_array().unwrap().iter().map(|v| v.as_str().unwrap()).collect();
        assert_eq!(origins, vec![format!("chrome-extension://{}/", "a".repeat(32)), format!("chrome-extension://{}/", "p".repeat(32))]);
    }

    #[test]
    fn only_vazie_vpn_is_recognised() {
        let vazie = json!({"permissions": ["nativeMessaging", "proxy", "storage"], "commands": {"toggle-connection": {}}});
        assert!(is_vazie_manifest(&vazie));
        assert!(!is_vazie_manifest(&json!({"permissions": ["nativeMessaging", "proxy"]})));
        assert!(!is_vazie_manifest(&json!({"permissions": ["storage"], "commands": {"toggle-connection": {}}})));
        assert!(!is_vazie_manifest(&json!({})));
    }

    #[test]
    fn extension_ids_are_checked() {
        assert!(is_extension_id(&"a".repeat(32)));
        assert!(!is_extension_id("short"));
        assert!(!is_extension_id(&"q".repeat(32)));
        assert!(!is_extension_id(&"A".repeat(32)));
    }
}
