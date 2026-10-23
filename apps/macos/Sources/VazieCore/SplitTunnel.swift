import Foundation

/// Split tunneling by site: which sites use the tunnel. A Mac routes by the system proxy, which knows names and not
/// programs, so the app's three modes (`SplitTunnel.kt`) are kept and the list holds sites where Android's holds apps:
/// everything, everything except the listed sites, or only the listed sites.
public enum SplitMode: String, Codable, Sendable, CaseIterable {
    case off, exclude, include
}

public struct SplitTunnel: Codable, Equatable, Sendable {
    public var mode: SplitMode
    public var sites: [String]

    public static let off = SplitTunnel(mode: .off, sites: [])
    public static let maxSites = 500

    public init(mode: SplitMode, sites: [String]) {
        self.mode = mode
        self.sites = sites
    }

    /// Whether anything is actually split: a mode with no sites chosen changes nothing.
    public var isActive: Bool { mode != .off && !sites.isEmpty }

    public static func mode(fromId id: String?) -> SplitMode {
        id == "exclude" ? .exclude : id == "include" ? .include : .off
    }

    private static let maxSiteLength = 253

    /// A site as it is stored — a bare lowercase host that also stands for its subdomains — from whatever a person
    /// typed or pasted: an address, `*.example.com`, a host with a port. `nil` when it is none of those.
    public static func normalizeSite(_ input: String) -> String? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if text.isEmpty { return nil }
        if text.contains("://") {
            guard let host = URL(string: text)?.host(percentEncoded: false), !host.isEmpty else { return nil }
            text = host
            // `URL` strips the brackets of an IPv6 literal; the stored form keeps them.
            if text.contains(":") && !text.hasPrefix("[") { text = "[\(text)]" }
        } else {
            if let cut = text.firstIndex(where: { "/?#".contains($0) }) { text = String(text[..<cut]) }
            // `host:port` and `[::1]:80`; a bare IPv6 literal has no port to cut.
            if !text.hasPrefix("[") {
                let parts = text.split(separator: ":", omittingEmptySubsequences: false)
                if parts.count == 2 { text = String(parts[0]) }
            } else if let close = text.firstIndex(of: "]") {
                text = String(text[...close])
            }
        }
        if text.hasPrefix("*.") { text.removeFirst(2) }
        if text.hasPrefix(".") { text.removeFirst() }
        if text.hasSuffix(".") { text.removeLast() }
        if text.isEmpty || text.utf8.count > maxSiteLength { return nil }
        if text.hasPrefix("[") && text.hasSuffix("]") {
            let inner = text.dropFirst().dropLast()
            return !inner.isEmpty && inner.utf8.allSatisfy({ isHexByte($0) || $0 == 0x3A || $0 == 0x2E }) ? text : nil
        }
        if text.utf8.allSatisfy({ ($0 >= 48 && $0 <= 57) || $0 == 0x2E }) { return isIPv4(text) ? text : nil }
        let labels = text.split(separator: ".", omittingEmptySubsequences: false)
        guard !labels.isEmpty, labels.allSatisfy({ isLabel(String($0)) }) else { return nil }
        return text
    }

    private static func isHexByte(_ b: UInt8) -> Bool { (b >= 48 && b <= 57) || (b >= 97 && b <= 102) }

    private static func isLabel(_ label: String) -> Bool {
        let b = Array(label.utf8)
        guard (1...63).contains(b.count), b.first != 0x2D, b.last != 0x2D else { return false }
        return b.allSatisfy { ($0 >= 97 && $0 <= 122) || ($0 >= 48 && $0 <= 57) || $0 == 0x2D }
    }

    private static func isIPv4(_ text: String) -> Bool {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        return parts.count == 4 && parts.allSatisfy { (1...3).contains($0.count) && Int($0).map { $0 <= 255 } == true }
    }

    /// The host of a page's address, as a stored site; `nil` for addresses that have none.
    public static func site(ofURL url: String?) -> String? {
        guard let url, let parsed = URL(string: url), let scheme = parsed.scheme?.lowercased(),
            scheme == "http" || scheme == "https", let host = parsed.host(percentEncoded: false)
        else { return nil }
        return normalizeSite(host.contains(":") ? "[\(host)]" : host)
    }

    /// The list with `input` added, or `nil` when it isn't a site or the list is full.
    public func adding(_ input: String) -> SplitTunnel? {
        guard let site = Self.normalizeSite(input) else { return nil }
        if sites.contains(site) { return self }
        if sites.count >= Self.maxSites { return nil }
        var copy = self
        copy.sites = (sites + [site]).sorted()
        return copy
    }

    public func removing(_ site: String) -> SplitTunnel {
        var copy = self
        copy.sites.removeAll { $0 == site }
        return copy
    }

    /// The same decision the PAC makes, for the tests and for the "this site" row.
    public func routesThroughTunnel(host: String) -> Bool {
        let h = host.lowercased()
        let listed = sites.contains { h == $0 || h.hasSuffix("." + $0) }
        if !isActive { return true }
        return mode == .exclude ? !listed : listed
    }

    /// Sites that are never sent to the tunnel server: it cannot reach a person's own machine or network.
    static let localSuffixes = [".local", ".localhost", ".lan", ".home.arpa", ".internal"]

    /// The proxy auto-config the system runs for every request. It fails closed: a request meant for the tunnel
    /// is answered with the tunnel's address alone, never with a fallback to `DIRECT`.
    public func pacScript(httpPort: Int) -> String {
        let sites = isActive ? self.sites : []
        let mode = isActive ? self.mode : .off
        func json(_ value: Any) -> String {
            let data = (try? JSONSerialization.data(withJSONObject: value, options: [.fragmentsAllowed, .withoutEscapingSlashes])) ?? Data("null".utf8)
            return String(decoding: data, as: UTF8.self)
        }
        return """
            // Generated by Vazie VPN. Do not edit.
            var PROXY = "PROXY 127.0.0.1:\(httpPort)";
            var MODE = \(json(mode.rawValue));
            var SITES = \(json(sites));
            var LOCAL_SUFFIXES = \(json(Self.localSuffixes));

            function isLocal(host) {
              if (host === "localhost" || (host.indexOf(".") < 0 && host.indexOf(":") < 0)) return true;
              for (var i = 0; i < LOCAL_SUFFIXES.length; i++) {
                var s = LOCAL_SUFFIXES[i];
                if (host.length > s.length && host.slice(-s.length) === s) return true;
              }
              var m = /^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)$/.exec(host);
              if (m) {
                var a = +m[1], b = +m[2];
                return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) ||
                  (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127);
              }
              if (host.charAt(0) === "[" || host.indexOf(":") >= 0) {
                var h = host.replace(/^\\[|\\]$/g, "").toLowerCase();
                return h === "::1" || h === "::" || /^f[cd]/.test(h) || /^fe[89ab]/.test(h);
              }
              return false;
            }

            function listed(host) {
              for (var i = 0; i < SITES.length; i++) {
                var s = SITES[i];
                if (host === s || (host.length > s.length && host.slice(-s.length - 1) === "." + s)) return true;
              }
              return false;
            }

            function FindProxyForURL(url, host) {
              host = host.toLowerCase();
              if (isLocal(host)) return "DIRECT";
              if (MODE === "exclude") return listed(host) ? "DIRECT" : PROXY;
              if (MODE === "include") return listed(host) ? PROXY : "DIRECT";
              return PROXY;
            }

            """
    }
}
