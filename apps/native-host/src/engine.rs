//! The Xray engine: pinned by version and SHA-256 per platform, downloaded from its own GitHub release and checked
//! before it is ever run — the same discipline the Android build applies to libXray (`fetchAndVerifyLibXray`).

use crate::paths;
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::{self, Read, Write};
use std::process::{Command, Stdio};
use std::time::Duration;

/// The Xray-core release the engine is taken from: the one libXray v26.7.28 in the Android app is built on.
pub const VERSION: &str = "v26.7.28";

struct Asset {
    /// `std::env::consts::OS` and `ARCH`.
    os: &'static str,
    arch: &'static str,
    file: &'static str,
    sha256: &'static str,
    /// SHA-256 of the unpacked program, where it was measured (the same value the macOS app pins). Elsewhere the hash
    /// taken at install time is kept next to the program.
    binary_sha256: Option<&'static str>,
}

const ASSETS: &[Asset] = &[
    Asset { os: "macos", arch: "aarch64", file: "Xray-macos-arm64-v8a.zip", sha256: "9b99a351febe31b7e0c7f22deeb1577a1da0b98aaa51aec7fd17832e68cf63d6", binary_sha256: Some("bd4154efa640c5b8e21f10b68afcc9177c4f1f543be3ec0485b10c499b2a4b27") },
    Asset { os: "macos", arch: "x86_64", file: "Xray-macos-64.zip", sha256: "812f7d9de6d3506795eabda2f6928ba301c632c3fe6fa39c52ea8e0ed9e4e244", binary_sha256: None },
    Asset { os: "linux", arch: "x86_64", file: "Xray-linux-64.zip", sha256: "8195d909f1109b8f3d99eefe401a3c451d7bf4af71f24d3815420f77e5dd2a40", binary_sha256: None },
    Asset { os: "linux", arch: "aarch64", file: "Xray-linux-arm64-v8a.zip", sha256: "f5698bb218ada3b4022db26fafc39601c5f53b46b19eb76c9616325985807501", binary_sha256: None },
    Asset { os: "windows", arch: "x86_64", file: "Xray-windows-64.zip", sha256: "c7172078fca4711bcd92a4774dcd1822544579c58816197575c47533317fd8d1", binary_sha256: None },
    Asset { os: "windows", arch: "aarch64", file: "Xray-windows-arm64-v8a.zip", sha256: "2d61646f79fdc6724e68a41eb235f6a7253cfac2809caa736ad065f6c10e14a2", binary_sha256: None },
];

fn asset() -> Option<&'static Asset> {
    ASSETS.iter().find(|a| a.os == std::env::consts::OS && a.arch == std::env::consts::ARCH)
}

pub fn platform() -> String {
    format!("{}-{}", std::env::consts::OS, std::env::consts::ARCH)
}

#[derive(Serialize)]
pub struct Status {
    pub installed: bool,
    pub supported: bool,
    pub version: &'static str,
}

pub fn status() -> Status {
    Status {
        installed: verify_binary(),
        supported: asset().is_some(),
        version: VERSION,
    }
}

/// The largest archive the helper will take from the network (the pinned ones are about 15 MB).
const MAX_DOWNLOAD_BYTES: u64 = 64 * 1024 * 1024;

/// Where the hash taken at install time is kept, for the platforms whose program has no pinned hash.
fn recorded_hash_path() -> std::path::PathBuf {
    paths::engine_binary(VERSION).with_extension("sha256")
}

fn hash_file(path: &std::path::Path) -> Option<String> {
    let mut file = File::open(path).ok()?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).ok()?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Some(hex(&hasher.finalize()))
}

/// Whether the engine on disk is the one that was installed: the pinned hash when there is one, else the hash recorded
/// at install. A missing or changed program is not run; the person is asked to install the engine again.
pub fn verify_binary() -> bool {
    let binary = paths::engine_binary(VERSION);
    if !binary.is_file() {
        return false;
    }
    let Some(actual) = hash_file(&binary) else { return false };
    let expected = match asset().and_then(|a| a.binary_sha256) {
        Some(pinned) => pinned.to_owned(),
        None => match fs::read_to_string(recorded_hash_path()) {
            Ok(text) => text.trim().to_owned(),
            Err(_) => return false,
        },
    };
    actual == expected
}

#[derive(Debug)]
pub enum InstallError {
    Unsupported,
    Download,
    Checksum,
    Archive,
    Disk,
}

impl InstallError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Unsupported => "unsupported_platform",
            Self::Download => "download",
            Self::Checksum => "checksum",
            Self::Archive => "archive",
            Self::Disk => "disk",
        }
    }
}

/// Downloads the pinned engine, refuses it unless its SHA-256 is the pinned one, and unpacks the executable.
/// `progress(done, total)` is called as bytes arrive.
pub fn install(mut progress: impl FnMut(u64, u64)) -> Result<(), InstallError> {
    let asset = asset().ok_or(InstallError::Unsupported)?;
    let url = format!("https://github.com/XTLS/Xray-core/releases/download/{VERSION}/{}", asset.file);
    let directory = paths::engine_dir(VERSION);
    fs::create_dir_all(&directory).map_err(|_| InstallError::Disk)?;
    let archive_path = directory.join(format!("{}.part", asset.file));

    let agent: ureq::Agent = ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(300)))
        .build()
        .into();
    let response = agent.get(&url).call().map_err(|_| InstallError::Download)?;
    let total = response
        .headers()
        .get("content-length")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse::<u64>().ok())
        .unwrap_or(0);
    if total > MAX_DOWNLOAD_BYTES {
        return Err(InstallError::Download);
    }

    let mut hasher = Sha256::new();
    {
        let mut file = File::create(&archive_path).map_err(|_| InstallError::Disk)?;
        let mut body = response.into_body().into_reader();
        let mut buffer = vec![0u8; 64 * 1024];
        let mut done = 0u64;
        let mut reported = 0u64;
        loop {
            let read = body.read(&mut buffer).map_err(|_| InstallError::Download)?;
            if read == 0 {
                break;
            }
            if done + read as u64 > MAX_DOWNLOAD_BYTES {
                drop(file);
                let _ = fs::remove_file(&archive_path);
                return Err(InstallError::Download);
            }
            hasher.update(&buffer[..read]);
            file.write_all(&buffer[..read]).map_err(|_| InstallError::Disk)?;
            done += read as u64;
            if done - reported >= 512 * 1024 {
                reported = done;
                progress(done, total);
            }
        }
        file.flush().map_err(|_| InstallError::Disk)?;
        progress(done, total.max(done));
    }

    let actual = hex(&hasher.finalize());
    if actual != asset.sha256 {
        let _ = fs::remove_file(&archive_path);
        return Err(InstallError::Checksum);
    }

    let binary = if cfg!(windows) { "xray.exe" } else { "xray" };
    let result = extract(&archive_path, binary, &paths::engine_binary(VERSION), asset.binary_sha256);
    let _ = fs::remove_file(&archive_path);
    result
}

fn extract(
    archive: &std::path::Path,
    entry: &str,
    destination: &std::path::Path,
    pinned: Option<&str>,
) -> Result<(), InstallError> {
    let file = File::open(archive).map_err(|_| InstallError::Disk)?;
    let mut zip = zip::ZipArchive::new(file).map_err(|_| InstallError::Archive)?;
    let source = zip.by_name(entry).map_err(|_| InstallError::Archive)?;
    let partial = destination.with_extension("part");
    {
        let mut out = File::create(&partial).map_err(|_| InstallError::Disk)?;
        io::copy(&mut source.take(MAX_DOWNLOAD_BYTES * 4), &mut out).map_err(|_| InstallError::Archive)?;
        out.flush().map_err(|_| InstallError::Disk)?;
    }
    // The archive's hash vouches for the archive; the program inside is checked too, so a pinned program cannot be
    // swapped for another one inside a correctly hashed archive, and the recorded hash is of what was really unpacked.
    let unpacked = hash_file(&partial).ok_or(InstallError::Disk)?;
    if let Some(pinned) = pinned
        && unpacked != pinned {
            let _ = fs::remove_file(&partial);
            return Err(InstallError::Checksum);
        }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&partial, fs::Permissions::from_mode(0o755)).map_err(|_| InstallError::Disk)?;
    }
    fs::rename(&partial, destination).map_err(|_| InstallError::Disk)?;
    fs::write(recorded_hash_path(), unpacked).map_err(|_| InstallError::Disk)
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// `Xray 26.7.28 (…)` from `xray version`, as the number alone.
pub fn running_version() -> Option<String> {
    let binary = paths::engine_binary(VERSION);
    if !binary.is_file() {
        return None;
    }
    let mut command = Command::new(binary);
    command.arg("version").stdin(Stdio::null()).stderr(Stdio::null());
    crate::session::hide_window(&mut command);
    let output = command.output().ok()?;
    String::from_utf8_lossy(&output.stdout)
        .lines()
        .next()?
        .split_whitespace()
        .nth(1)
        .map(str::to_owned)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_pinned_checksum_is_a_sha256() {
        for asset in ASSETS {
            assert_eq!(asset.sha256.len(), 64, "{}", asset.file);
            assert!(asset.sha256.chars().all(|c| c.is_ascii_hexdigit()), "{}", asset.file);
        }
    }

    #[test]
    fn hex_is_lowercase_and_padded() {
        assert_eq!(hex(&[0, 15, 255]), "000fff");
    }
}
