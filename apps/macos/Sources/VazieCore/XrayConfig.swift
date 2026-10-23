import Foundation

/// What Xray runs, ported from `XrayConfigFactory.kt` (outbounds) and the helper's `compose_config` (inbounds).
///
/// The inbounds are loopback-only: an HTTP proxy and a SOCKS proxy for the system proxy settings, and the stats
/// API. The user's keys reach the engine through a pipe, never through a file.
public enum XrayConfig {
    public static let tagProxy = "proxy"
    public static let tagBlock = "block"

    private static let defaultFingerprint = "chrome"
    private static let defaultSpiderX = "/"

    /// Post-quantum ClientHellos (>1700 bytes) can hang REALITY on lossy paths, so the last pre-quantum build of the
    /// requested browser is used.
    private static let preQuantumBuilds = ["chrome": "hellochrome_120", "firefox": "hellofirefox_120", "edge": "helloedge_106"]

    static func preQuantumFingerprint(_ requested: String?) -> String {
        let name = requested?.lowercased() ?? defaultFingerprint
        return preQuantumBuilds[name] ?? name
    }

    private static func streamSettings(_ outbound: VlessOutbound) -> [String: Any] {
        var out: [String: Any] = [
            // Xray renamed the TCP stream to `raw` and kept `tcp` as an alias; the current name is written.
            "network": outbound.transport.kind == .tcp ? "raw" : outbound.transport.kind.rawValue,
            "security": outbound.security.kind.rawValue,
        ]
        switch outbound.security {
        case .none:
            break
        case let .tls(serverName, fingerprint, alpn, _):
            var tls: [String: Any] = [:]
            if let serverName, !serverName.isEmpty { tls["serverName"] = serverName }
            if let fingerprint, !fingerprint.isEmpty { tls["fingerprint"] = preQuantumFingerprint(fingerprint) }
            if !alpn.isEmpty { tls["alpn"] = alpn }
            // No `allowInsecure`: `Support.check` refuses profiles that ask for it.
            out["tlsSettings"] = tls
        case let .reality(serverName, fingerprint, publicKey, shortId, spiderX):
            var reality: [String: Any] = [:]
            if let serverName, !serverName.isEmpty { reality["serverName"] = serverName }
            reality["fingerprint"] = preQuantumFingerprint(fingerprint)
            reality["publicKey"] = publicKey
            reality["shortId"] = shortId ?? ""
            reality["spiderX"] = spiderX ?? defaultSpiderX
            out["realitySettings"] = reality
        }
        switch outbound.transport {
        case let .tcp(header):
            out["rawSettings"] = ["header": ["type": header ?? "none"]]
        case let .ws(path, host):
            var ws: [String: Any] = ["path": path.isEmpty ? "/" : path]
            if let host, !host.isEmpty { ws["host"] = host }
            out["wsSettings"] = ws
        case let .grpc(serviceName, multiMode):
            out["grpcSettings"] = ["serviceName": serviceName, "multiMode": multiMode]
        }
        return out
    }

    /// The outbounds Xray is started with: the user's server, and a blackhole.
    public static func outbounds(_ outbound: VlessOutbound) -> [[String: Any]] {
        var user: [String: Any] = ["id": outbound.userId, "encryption": "none", "level": 0]
        if let flow = outbound.flow { user["flow"] = flow.rawValue }
        return [
            [
                "tag": tagProxy,
                "protocol": "vless",
                "settings": ["vnext": [["address": outbound.endpoint.host, "port": outbound.endpoint.port, "users": [user]]]],
                "streamSettings": streamSettings(outbound),
            ],
            ["tag": tagBlock, "protocol": "blackhole"],
        ]
    }

    /// The complete document the engine reads from its standard input.
    public static func compose(outbound: VlessOutbound, httpPort: Int, socksPort: Int, apiPort: Int) -> [String: Any] {
        [
            "log": ["loglevel": "none"],
            "stats": [String: Any](),
            "api": ["tag": "api", "services": ["StatsService"]],
            "policy": ["system": ["statsOutboundUplink": true, "statsOutboundDownlink": true]],
            "inbounds": [
                [
                    "tag": "http-in", "listen": "127.0.0.1", "port": httpPort, "protocol": "http",
                    "settings": ["allowTransparent": false, "userLevel": 0],
                ],
                [
                    "tag": "socks-in", "listen": "127.0.0.1", "port": socksPort, "protocol": "socks",
                    "settings": ["auth": "noauth", "udp": false, "userLevel": 0],
                ],
                [
                    "tag": "api", "listen": "127.0.0.1", "port": apiPort, "protocol": "dokodemo-door",
                    "settings": ["address": "127.0.0.1"],
                ],
            ],
            "outbounds": outbounds(outbound),
            "routing": [
                "domainStrategy": "AsIs",
                "rules": [
                    ["type": "field", "inboundTag": ["api"], "outboundTag": "api"],
                    ["type": "field", "inboundTag": ["http-in", "socks-in"], "outboundTag": tagProxy],
                ],
            ],
        ]
    }

    /// Networks that are never sent to the tunnel server: the Mac's own, which it cannot reach.
    static let privateRanges = [
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "100.64.0.0/10", "127.0.0.0/8", "224.0.0.0/4",
        "fc00::/7", "fe80::/10", "ff00::/8",
    ]

    /// The configuration for the whole-Mac mode: a TUN inbound that takes the Mac's traffic (Xray adds the routes and
    /// keeps its own connections off them), beside the loopback proxies the app probes and counts through. A split, here,
    /// is by site, decided by the names the engine reads from the traffic.
    public static func composeTun(
        outbound: VlessOutbound, httpPort: Int, socksPort: Int, apiPort: Int, tunName: String, split: SplitTunnel, interface: String? = nil
    ) -> [String: Any] {
        var rules: [[String: Any]] = [
            ["type": "field", "inboundTag": ["api"], "outboundTag": "api"],
            ["type": "field", "inboundTag": ["tun-in"], "ip": privateRanges, "outboundTag": "direct"],
        ]
        if split.isActive {
            // Names are looked up through the tunnel whatever the split says: a DNS query carries no site to match, so
            // without this rule "only these sites" would send the lookups for those very sites out directly.
            rules.append(["type": "field", "inboundTag": ["tun-in"], "network": "tcp,udp", "port": "53", "outboundTag": tagProxy])
            let domains = split.sites.map { "domain:\($0)" }
            switch split.mode {
            case .exclude:
                rules.append(["type": "field", "inboundTag": ["tun-in"], "domain": domains, "outboundTag": "direct"])
            case .include:
                rules.append(["type": "field", "inboundTag": ["tun-in"], "domain": domains, "outboundTag": tagProxy])
                rules.append(["type": "field", "inboundTag": ["tun-in"], "network": "tcp,udp", "outboundTag": "direct"])
            case .off:
                break
            }
        }
        rules.append(["type": "field", "inboundTag": ["tun-in", "http-in", "socks-in"], "outboundTag": tagProxy])

        var outbounds = XrayConfig.outbounds(outbound)
        var direct: [String: Any] = ["tag": "direct", "protocol": "freedom"]
        if let interface {
            // What goes direct must leave by the physical interface, not come back into the tunnel.
            direct["streamSettings"] = ["sockopt": ["interface": interface]]
            var proxy = outbounds[0]
            var stream = proxy["streamSettings"] as? [String: Any] ?? [:]
            stream["sockopt"] = ["interface": interface]
            proxy["streamSettings"] = stream
            outbounds[0] = proxy
        }
        outbounds.append(direct)
        return [
            "log": ["loglevel": "none"],
            "stats": [String: Any](),
            "api": ["tag": "api", "services": ["StatsService"]],
            "policy": ["system": ["statsOutboundUplink": true, "statsOutboundDownlink": true]],
            "inbounds": [
                [
                    "tag": "tun-in", "protocol": "tun",
                    "settings": [
                        // Only the device: its address and the routes into it are the script's.
                        "name": tunName, "MTU": 1500,
                    ],
                    "sniffing": ["enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": true],
                ],
                ["tag": "http-in", "listen": "127.0.0.1", "port": httpPort, "protocol": "http", "settings": ["allowTransparent": false, "userLevel": 0]],
                ["tag": "socks-in", "listen": "127.0.0.1", "port": socksPort, "protocol": "socks", "settings": ["auth": "noauth", "udp": false, "userLevel": 0]],
                ["tag": "api", "listen": "127.0.0.1", "port": apiPort, "protocol": "dokodemo-door", "settings": ["address": "127.0.0.1"]],
            ],
            "outbounds": outbounds,
            "routing": ["domainStrategy": "AsIs", "rules": rules],
        ]
    }

    /// The server's address as an address: the tunnel cannot look the name up through itself, so it is looked up before. For
    /// TLS and REALITY the name stays as the server name when none was given.
    public static func pinned(_ outbound: VlessOutbound, to address: String) -> VlessOutbound {
        var copy = outbound
        let name = outbound.endpoint.host
        copy.endpoint.host = address
        guard address != name else { return copy }
        switch outbound.security {
        case .none:
            break
        case let .tls(serverName, fingerprint, alpn, insecure):
            copy.security = .tls(serverName: serverName ?? name, fingerprint: fingerprint, alpn: alpn, allowInsecure: insecure)
        case let .reality(serverName, fingerprint, key, shortId, spiderX):
            copy.security = .reality(serverName: serverName ?? name, fingerprint: fingerprint, publicKey: key, shortId: shortId, spiderX: spiderX)
        }
        // A WebSocket's Host header is the name too.
        if case let .ws(path, host) = outbound.transport, host == nil { copy.transport = .ws(path: path, host: name) }
        return copy
    }

    public static func json(_ document: [String: Any]) throws -> Data {
        try JSONSerialization.data(withJSONObject: document, options: [.sortedKeys, .withoutEscapingSlashes])
    }
}

/// What the pinned Xray build can execute, asked before anything is started (`XraySupport.kt`).
public enum Support {
    public enum Unsupported: Equatable, Sendable { case engine, protocolKind, transport, security, flow }

    private static let unsafeFingerprint = "unsafe"
    /// Matches Xray's `hello…` fingerprint family by prefix instead of copying its tables.
    private static let explicitFingerprintPrefix = "hello"
    private static let presetFingerprints: Set<String> = [
        "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized", "randomizednoalpn",
    ]

    private static func isKnownFingerprint(_ fingerprint: String?) -> Bool {
        let name = fingerprint?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        if name.isEmpty { return true }
        if name == unsafeFingerprint { return false }
        return presetFingerprints.contains(name) || name.hasPrefix(explicitFingerprintPrefix)
    }

    public static func check(_ outbound: VlessOutbound) -> Unsupported? {
        if case let .tls(_, _, _, allowInsecure) = outbound.security, allowInsecure { return .security }
        if !isKnownFingerprint(outbound.security.fingerprint) { return .security }
        if outbound.security.kind == .reality && outbound.transport.kind == .ws { return .security }
        // Vision needs TLS or REALITY over RAW/TCP; the engine checks it itself.
        if outbound.flow == .vision {
            if outbound.transport.kind != .tcp { return .flow }
            if outbound.security.kind == .none { return .flow }
        }
        return nil
    }

    public static func isRunnable(_ outbound: VlessOutbound) -> Bool { check(outbound) == nil }
}
