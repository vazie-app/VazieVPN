import Foundation

// The normalized configuration, as `vpn/api/XrayOutbound.kt` has it (and `shared/model.ts` in the extension).
// Secrets are plain strings here: this model lives only inside the vault and the parser. Nothing the interface
// receives for display contains one.

public struct Endpoint: Codable, Equatable, Sendable {
    public var host: String
    public var port: Int

    public init(host: String, port: Int) {
        self.host = host
        self.port = port
    }
}

public enum SecurityKind: String, Codable, Sendable { case none, tls, reality }
public enum TransportKind: String, Codable, Sendable { case tcp, ws, grpc }
public enum XrayFlow: String, Codable, Sendable { case vision = "xtls-rprx-vision" }

public enum Security: Codable, Equatable, Sendable {
    case none
    case tls(serverName: String?, fingerprint: String?, alpn: [String], allowInsecure: Bool)
    case reality(serverName: String?, fingerprint: String?, publicKey: String, shortId: String?, spiderX: String?)

    public var kind: SecurityKind {
        switch self {
        case .none: return .none
        case .tls: return .tls
        case .reality: return .reality
        }
    }

    public var serverName: String? {
        switch self {
        case .none: return nil
        case let .tls(name, _, _, _): return name
        case let .reality(name, _, _, _, _): return name
        }
    }

    public var fingerprint: String? {
        switch self {
        case .none: return nil
        case let .tls(_, print, _, _): return print
        case let .reality(_, print, _, _, _): return print
        }
    }
}

public enum Transport: Codable, Equatable, Sendable {
    case tcp(headerType: String?)
    case ws(path: String, host: String?)
    case grpc(serviceName: String, multiMode: Bool)

    public var kind: TransportKind {
        switch self {
        case .tcp: return .tcp
        case .ws: return .ws
        case .grpc: return .grpc
        }
    }
}

public struct UnknownParameter: Codable, Equatable, Sendable {
    public var name: String
    public var value: String

    public init(name: String, value: String) {
        self.name = name
        self.value = value
    }
}

public struct VlessOutbound: Codable, Equatable, Sendable {
    public var userId: String
    public var endpoint: Endpoint
    public var security: Security
    public var transport: Transport
    public var flow: XrayFlow?
    public var unknownParameters: [UnknownParameter]

    public init(
        userId: String, endpoint: Endpoint, security: Security, transport: Transport, flow: XrayFlow? = nil,
        unknownParameters: [UnknownParameter] = []
    ) {
        self.userId = userId
        self.endpoint = endpoint
        self.security = security
        self.transport = transport
        self.flow = flow
        self.unknownParameters = unknownParameters
    }
}

public struct StoredProfile: Codable, Equatable, Sendable, Identifiable {
    public var id: String
    public var name: String
    /// Milliseconds since the epoch, as the other clients store them.
    public var createdAt: Int64
    public var lastUsedAt: Int64?
    public var outbound: VlessOutbound

    public init(id: String, name: String, createdAt: Int64, lastUsedAt: Int64? = nil, outbound: VlessOutbound) {
        self.id = id
        self.name = name
        self.createdAt = createdAt
        self.lastUsedAt = lastUsedAt
        self.outbound = outbound
    }
}

/// What a draft is before it has a name and an id.
public struct ProfileDraft: Equatable, Sendable {
    public var suggestedName: String?
    public var outbound: VlessOutbound

    public init(suggestedName: String?, outbound: VlessOutbound) {
        self.suggestedName = suggestedName
        self.outbound = outbound
    }
}

public enum Protocol_ {
    public static let id = "vless"
    public static let label = "VLESS"
    public static let mark = "VL"
}

public func nowMillis() -> Int64 {
    Int64((Date().timeIntervalSince1970 * 1000).rounded())
}
