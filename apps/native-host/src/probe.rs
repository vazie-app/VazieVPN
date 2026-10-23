//! The two checks a person's connection needs: how long a server takes to answer, and whether the tunnel carries
//! traffic at all.

use std::net::{TcpStream, ToSocketAddrs};
use std::time::{Duration, Instant};

const ATTEMPTS: u32 = 2;
const TIMEOUT: Duration = Duration::from_secs(3);

/// Milliseconds for a TCP handshake to `host:port`, the best of two tries, over this machine's own network and
/// not the tunnel; `None` when the server did not answer in time.
pub fn latency(host: &str, port: u16) -> Option<u64> {
    let addresses: Vec<_> = (host, port).to_socket_addrs().ok()?.collect();
    let address = addresses.first()?;
    (0..ATTEMPTS)
        .filter_map(|_| {
            let started = Instant::now();
            TcpStream::connect_timeout(address, TIMEOUT).ok()?;
            Some((started.elapsed().as_millis() as u64).max(1))
        })
        .min()
}

#[derive(Debug, PartialEq, Eq)]
pub enum Stage {
    /// A TLS handshake to a literal address, through the tunnel.
    Tls,
    /// A name resolved and reached, through the tunnel.
    Dns,
}

impl Stage {
    pub fn code(&self) -> &'static str {
        match self {
            Self::Tls => "tls",
            Self::Dns => "dns",
        }
    }
}

fn agent(port: u16) -> Option<ureq::Agent> {
    let proxy = ureq::Proxy::new(&format!("http://127.0.0.1:{port}")).ok()?;
    let config = ureq::Agent::config_builder()
        .proxy(Some(proxy))
        .timeout_global(Some(Duration::from_secs(10)))
        .max_redirects(0)
        .http_status_as_error(false)
        .build();
    Some(config.into())
}

/// Proves the tunnel is usable in the two ways it can separately fail, the way the Android app does: reach an
/// address and finish TLS with it, then reach a name. Any answer at all is a pass; no answer is not.
pub fn readiness(port: u16) -> Result<(), Stage> {
    let agent = agent(port).ok_or(Stage::Tls)?;
    agent.get("https://1.1.1.1/").call().map_err(|_| Stage::Tls)?;
    agent.get("https://cp.cloudflare.com/generate_204").call().map_err(|_| Stage::Dns)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_closed_port_has_no_latency() {
        // Port 1 on loopback is not listening anywhere sensible.
        assert!(latency("127.0.0.1", 1).is_none());
    }

    #[test]
    fn an_open_port_answers() {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        assert!(latency("127.0.0.1", port).is_some());
    }

}
