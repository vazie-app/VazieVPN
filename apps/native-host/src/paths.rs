//! Where the helper keeps things: its own copy of the binary, the engine, and (on Windows) the browser manifest.

use std::env;
use std::path::PathBuf;

pub const HOST_NAME: &str = "app.vazie.vpn";

/// The per-user data directory: `…/Vazie VPN` on macOS and Windows, `…/vazie-vpn` on Linux.
pub fn data_dir() -> PathBuf {
    #[cfg(target_os = "macos")]
    {
        home().join("Library/Application Support/Vazie VPN")
    }
    #[cfg(target_os = "windows")]
    {
        env::var_os("LOCALAPPDATA").map(PathBuf::from).unwrap_or_else(home).join("Vazie VPN")
    }
    #[cfg(not(any(target_os = "macos", target_os = "windows")))]
    {
        env::var_os("XDG_DATA_HOME")
            .map(PathBuf::from)
            .filter(|p| p.is_absolute())
            .unwrap_or_else(|| home().join(".local/share"))
            .join("vazie-vpn")
    }
}

pub fn home() -> PathBuf {
    #[cfg(target_os = "windows")]
    let variable = "USERPROFILE";
    #[cfg(not(target_os = "windows"))]
    let variable = "HOME";
    env::var_os(variable).map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."))
}

pub fn bin_dir() -> PathBuf {
    data_dir().join("bin")
}

pub fn installed_host_path() -> PathBuf {
    bin_dir().join(if cfg!(windows) { "vazie-vpn-host.exe" } else { "vazie-vpn-host" })
}

pub fn engine_dir(version: &str) -> PathBuf {
    data_dir().join("engine").join(version)
}

pub fn engine_binary(version: &str) -> PathBuf {
    engine_dir(version).join(if cfg!(windows) { "xray.exe" } else { "xray" })
}
