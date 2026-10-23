import Foundation

public enum VlessSerializer {
    private typealias Pairs = [(String, String)]

    private static func securityParameters(_ security: Security) -> Pairs {
        var out: Pairs = []
        switch security {
        case .none:
            break
        case let .tls(serverName, fingerprint, alpn, allowInsecure):
            if let serverName, !serverName.isEmpty { out.append(("sni", serverName)) }
            if let fingerprint, !fingerprint.isEmpty { out.append(("fp", fingerprint)) }
            if !alpn.isEmpty { out.append(("alpn", alpn.joined(separator: ","))) }
            if allowInsecure { out.append(("allowInsecure", "1")) }
        case let .reality(serverName, fingerprint, publicKey, shortId, spiderX):
            if let serverName, !serverName.isEmpty { out.append(("sni", serverName)) }
            if let fingerprint, !fingerprint.isEmpty { out.append(("fp", fingerprint)) }
            out.append(("pbk", publicKey))
            if let shortId, !shortId.isEmpty { out.append(("sid", shortId)) }
            if let spiderX, !spiderX.isEmpty { out.append(("spx", spiderX)) }
        }
        return out
    }

    private static func transportParameters(_ transport: Transport) -> Pairs {
        switch transport {
        case let .tcp(header):
            if let header, !header.isEmpty { return [("headerType", header)] }
            return []
        case let .ws(path, host):
            var out: Pairs = [("path", path)]
            if let host, !host.isEmpty { out.append(("host", host)) }
            return out
        case let .grpc(serviceName, multiMode):
            var out: Pairs = []
            if !serviceName.isEmpty { out.append(("serviceName", serviceName)) }
            if multiMode { out.append(("mode", "multi")) }
            return out
        }
    }

    /// Turns a stored configuration back into the `vless://` link it came from.
    public static func serialize(_ outbound: VlessOutbound, name: String?) -> String {
        var parameters: Pairs = [
            // Written even though the parser treats an absent value the same way: other clients are less forgiving.
            ("encryption", "none"),
            ("type", outbound.transport.kind.rawValue),
            ("security", outbound.security.kind.rawValue),
        ]
        parameters += securityParameters(outbound.security)
        parameters += transportParameters(outbound.transport)
        if let flow = outbound.flow { parameters.append(("flow", flow.rawValue)) }
        // An unknown parameter is somebody else's field: hand it back exactly as it arrived.
        for entry in outbound.unknownParameters { parameters.append((entry.name, entry.value)) }

        let host = outbound.endpoint.host.contains(":") ? "[\(outbound.endpoint.host)]" : outbound.endpoint.host
        var link = "vless://\(Percent.encode(outbound.userId, safe: Percent.userInfo))@\(host):\(outbound.endpoint.port)"
        for (index, pair) in parameters.enumerated() {
            link += index == 0 ? "?" : "&"
            link += "\(Percent.encode(pair.0, safe: Percent.query))=\(Percent.encode(pair.1, safe: Percent.query))"
        }
        if let label = name?.trimmingCharacters(in: .whitespacesAndNewlines), !label.isEmpty {
            link += "#\(Percent.encode(label, safe: Percent.fragment))"
        }
        return link
    }
}
