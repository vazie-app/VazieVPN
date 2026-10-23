import Foundation

public enum UnsupportedFeature: Equatable, Sendable {
    case security(String)
    case transport(String)
    case flow(String)
    case encryption(String)

    public var feature: String {
        switch self {
        case .security: return "security"
        case .transport: return "transport"
        case .flow: return "flow"
        case .encryption: return "encryption"
        }
    }

    public var value: String {
        switch self {
        case let .security(v), let .transport(v), let .flow(v), let .encryption(v): return v
        }
    }
}

public enum InvalidReason: Equatable, Sendable {
    case empty
    case unknownScheme(String?)
    case malformedURI
    case missingUserID
    case malformedUserID
    case missingHost
    case malformedHost
    case missingPort
    case invalidPort
    case malformedEncoding
    case missingParameter(String)
    case conflictingParameters([String])

    /// The vocabulary the shared corpus uses.
    public var name: String {
        switch self {
        case .empty: return "empty"
        case .unknownScheme: return "unknownScheme"
        case .malformedURI: return "malformedURI"
        case .missingUserID: return "missingUserID"
        case .malformedUserID: return "malformedUserID"
        case .missingHost: return "missingHost"
        case .malformedHost: return "malformedHost"
        case .missingPort: return "missingPort"
        case .invalidPort: return "invalidPort"
        case .malformedEncoding: return "malformedEncoding"
        case .missingParameter: return "missingParameter"
        case .conflictingParameters: return "conflictingParameters"
        }
    }
}

public enum ParseResult: Equatable, Sendable {
    case recognized(ProfileDraft)
    case unsupported(UnsupportedFeature)
    case invalid(InvalidReason)
}

extension String {
    /// Index (in UTF-8 bytes) of the first `byte`.
    fileprivate func firstByte(_ byte: UInt8) -> Int? { utf8.firstIndex(of: byte).map { utf8.distance(from: utf8.startIndex, to: $0) } }
    fileprivate func lastByte(_ byte: UInt8) -> Int? { utf8.lastIndex(of: byte).map { utf8.distance(from: utf8.startIndex, to: $0) } }

    /// A slice by UTF-8 byte offsets; the offsets always sit on ASCII delimiters, so they are on scalar boundaries.
    fileprivate func bytes(from: Int, to: Int? = nil) -> String {
        let all = Array(utf8)
        let end = to ?? all.count
        if from >= end { return "" }
        return String(decoding: all[from..<end], as: UTF8.self)
    }

    fileprivate var trimmedOrNil: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

private func trimOrNull(_ text: String?) -> String? { text?.trimmedOrNil }

private func isTruthy(_ text: String?) -> Bool {
    guard let text else { return false }
    return text == "1" || text.lowercased() == "true"
}

private func isAlnum(_ b: UInt8) -> Bool { (b >= 48 && b <= 57) || (b >= 65 && b <= 90) || (b >= 97 && b <= 122) }
private func isHex(_ b: UInt8) -> Bool { (b >= 48 && b <= 57) || (b >= 65 && b <= 70) || (b >= 97 && b <= 102) }

/// A domain name or an IPv4 literal, permissively: hosts are the user's, not ours to judge.
func isPlainHost(_ text: String) -> Bool {
    let b = Array(text.utf8)
    guard let first = b.first, let last = b.last, isAlnum(first), isAlnum(last) else { return false }
    return b.allSatisfy { isAlnum($0) || $0 == 0x2E || $0 == 0x5F || $0 == 0x2D }
}

/// Enough to tell an IPv6 literal from a typo; the OS resolves it, not this parser.
func isIPv6Literal(_ text: String) -> Bool {
    var body = text
    if let zone = text.firstByte(0x25) {
        let name = Array(text.utf8)[(zone + 1)...]
        guard !name.isEmpty, name.allSatisfy({ isAlnum($0) || $0 == 0x2E || $0 == 0x5F || $0 == 0x2D }) else { return false }
        body = text.bytes(from: 0, to: zone)
    }
    let b = Array(body.utf8)
    guard let firstColon = b.firstIndex(of: 0x3A) else { return false }
    // Everything before the first colon is hexadecimal; after it hexadecimal, colons and dots.
    guard b[..<firstColon].allSatisfy(isHex) else { return false }
    return b[(firstColon + 1)...].allSatisfy { isHex($0) || $0 == 0x3A || $0 == 0x2E }
}

func isUUID(_ text: String) -> Bool {
    let b = Array(text.utf8)
    guard b.count == 36 else { return false }
    for (i, c) in b.enumerated() {
        if [8, 13, 18, 23].contains(i) {
            if c != 0x2D { return false }
        } else if !isHex(c) {
            return false
        }
    }
    return true
}

public enum VlessParser {
    static let scheme = "vless://"
    private static let maxNameLength = 80
    private static let defaultWebSocketPath = "/"

    public static func accepts(_ raw: String) -> Bool {
        raw.count >= scheme.count && raw.lowercased().hasPrefix(scheme)
    }

    private enum HostPort {
        case ok(Endpoint)
        case failed(InvalidReason)
    }

    private static func parseHostPort(_ text: String) -> HostPort {
        if text.isEmpty { return .failed(.missingHost) }
        let host: String
        let rawPort: String
        if text.hasPrefix("[") {
            guard let close = text.firstByte(0x5D) else { return .failed(.malformedURI) }
            host = text.bytes(from: 1, to: close)
            let after = text.bytes(from: close + 1)
            if after.isEmpty { return .failed(.missingPort) }
            if !after.hasPrefix(":") { return .failed(.malformedURI) }
            rawPort = String(after.dropFirst())
            if host.isEmpty { return .failed(.missingHost) }
            if !isIPv6Literal(host) { return .failed(.malformedHost) }
        } else {
            guard let colon = text.lastByte(0x3A) else { return .failed(.missingPort) }
            host = text.bytes(from: 0, to: colon)
            rawPort = text.bytes(from: colon + 1)
            if host.isEmpty { return .failed(.missingHost) }
            if !isPlainHost(host) { return .failed(.malformedHost) }
        }
        if rawPort.isEmpty { return .failed(.missingPort) }
        guard rawPort.utf8.allSatisfy({ $0 >= 48 && $0 <= 57 }), let port = Int(rawPort), port >= 1, port <= 65535 else {
            return .failed(.invalidPort)
        }
        return .ok(Endpoint(host: host, port: port))
    }

    /// Reads a `vless://` share link.
    public static func parse(_ raw: String) -> ParseResult {
        guard accepts(raw) else { return .invalid(.unknownScheme(nil)) }

        var rest = String(raw.dropFirst(scheme.count))
        var rawFragment: String?
        if let hash = rest.firstByte(0x23) {
            rawFragment = rest.bytes(from: hash + 1)
            rest = rest.bytes(from: 0, to: hash)
        }

        var rawQuery = ""
        var authority = rest
        if let question = rest.firstByte(0x3F) {
            rawQuery = rest.bytes(from: question + 1)
            authority = rest.bytes(from: 0, to: question)
        }

        guard let at = authority.lastByte(0x40) else { return .invalid(.missingUserID) }
        let rawUserId = authority.bytes(from: 0, to: at)
        if rawUserId.isEmpty { return .invalid(.missingUserID) }

        guard let userId = Percent.decodeStrict(rawUserId) else { return .invalid(.malformedEncoding) }
        if !isUUID(userId) { return .invalid(.malformedUserID) }

        let endpoint: Endpoint
        switch parseHostPort(authority.bytes(from: at + 1)) {
        case let .ok(e): endpoint = e
        case let .failed(reason): return .invalid(reason)
        }

        guard let query = LinkQuery.parse(rawQuery) else { return .invalid(.malformedEncoding) }

        let encryption = query.take("encryption")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !encryption.isEmpty && encryption.lowercased() != "none" { return .unsupported(.encryption(encryption)) }

        var rawSecurity = query.take("security")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if rawSecurity.isEmpty { rawSecurity = "none" }
        guard let securityKind = SecurityKind(rawValue: rawSecurity.lowercased()) else {
            return .unsupported(.security(rawSecurity))
        }

        var rawTransport = query.take("type")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if rawTransport.isEmpty { rawTransport = "tcp" }
        let transportKind: TransportKind
        switch rawTransport.lowercased() {
        case "tcp": transportKind = .tcp
        case "ws", "websocket": transportKind = .ws
        case "grpc": transportKind = .grpc
        default: return .unsupported(.transport(rawTransport))
        }

        let rawFlow = query.take("flow")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        var flow: XrayFlow?
        if !rawFlow.isEmpty {
            if rawFlow.lowercased() == "xtls-rprx-vision" { flow = .vision } else { return .unsupported(.flow(rawFlow)) }
        }

        // REALITY runs over TCP, HTTP/2 and gRPC; there is no WebSocket variant of it.
        if securityKind == .reality && transportKind == .ws { return .invalid(.conflictingParameters(["security", "type"])) }
        if flow != nil && securityKind == .none { return .invalid(.conflictingParameters(["flow", "security"])) }
        if flow != nil && transportKind != .tcp { return .invalid(.conflictingParameters(["flow", "type"])) }

        // `sni` is the parameter every current client writes; `peer` is what older ones wrote for the same field.
        func takeServerName() -> String? { trimOrNull(query.take("sni", "peer")) }

        let security: Security
        switch securityKind {
        case .none:
            security = .none
        case .tls:
            let serverName = takeServerName()
            let fingerprint = trimOrNull(query.take("fp", "fingerprint"))
            let alpn = (query.take("alpn") ?? "").split(separator: ",", omittingEmptySubsequences: false)
                .compactMap { trimOrNull(String($0)) }
            let insecure = isTruthy(query.take("allowInsecure", "insecure"))
            security = .tls(serverName: serverName, fingerprint: fingerprint, alpn: alpn, allowInsecure: insecure)
        case .reality:
            let serverName = takeServerName()
            let fingerprint = trimOrNull(query.take("fp", "fingerprint"))
            guard let publicKey = trimOrNull(query.take("pbk", "publicKey")) else {
                return .invalid(.missingParameter("pbk"))
            }
            security = .reality(
                serverName: serverName, fingerprint: fingerprint, publicKey: publicKey,
                shortId: trimOrNull(query.take("sid", "shortId")), spiderX: trimOrNull(query.take("spx", "spiderX")))
        }

        let transport: Transport
        switch transportKind {
        case .tcp:
            transport = .tcp(headerType: trimOrNull(query.take("headerType")))
        case .ws:
            transport = .ws(path: trimOrNull(query.take("path")) ?? defaultWebSocketPath, host: trimOrNull(query.take("host")))
        case .grpc:
            let service = query.take("serviceName")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let multi = (query.take("mode")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "").lowercased() == "multi"
            transport = .grpc(serviceName: service, multiMode: multi)
        }

        var suggestedName: String?
        if let rawFragment {
            let decoded = Percent.decodeLenient(rawFragment).trimmingCharacters(in: .whitespacesAndNewlines)
            if !decoded.isEmpty {
                var scalars = String.UnicodeScalarView()
                scalars.append(contentsOf: decoded.unicodeScalars.prefix(maxNameLength))
                suggestedName = String(scalars)
            }
        }

        return .recognized(
            ProfileDraft(
                suggestedName: suggestedName,
                outbound: VlessOutbound(
                    userId: userId, endpoint: endpoint, security: security, transport: transport, flow: flow,
                    unknownParameters: query.remaining().map { UnknownParameter(name: $0.0, value: $0.1) })))
    }
}

/// The first parser that accepts the input answers for it. One today; the list is the extension point.
public enum Parsers {
    /// The formats a screen may tell a person Vazie reads, as one already-formatted phrase.
    public static let advertisedFormats = "VLESS"

    /// A scheme, if the text starts with one. Anything that is not `word://` reports none rather than guessing.
    static func scheme(of text: String) -> String? {
        guard let range = text.range(of: "://") else { return nil }
        let candidate = String(text[..<range.lowerBound])
        let b = Array(candidate.utf8)
        guard (1...32).contains(b.count), let first = b.first, (first >= 65 && first <= 90) || (first >= 97 && first <= 122) else { return nil }
        guard b.allSatisfy({ isAlnum($0) || $0 == 0x2B || $0 == 0x2E || $0 == 0x2D }) else { return nil }
        return candidate.lowercased()
    }

    public static func parse(_ raw: String) -> ParseResult {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return .invalid(.empty) }
        if !VlessParser.accepts(trimmed) { return .invalid(.unknownScheme(scheme(of: trimmed))) }
        return VlessParser.parse(trimmed)
    }
}
