import Foundation

public enum Format {
    /// `252` → `04:12`; hours appear only once there are any (`formatSessionDuration`).
    public static func sessionDuration(seconds: Int) -> String {
        let safe = max(0, seconds)
        let hours = safe / 3600
        let minutes = (safe % 3600) / 60
        let rest = safe % 60
        func two(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }
        return hours > 0 ? "\(hours):\(two(minutes)):\(two(rest))" : "\(two(minutes)):\(two(rest))"
    }

    public enum ByteUnit: String, Sendable { case b = "B", kb = "KB", mb = "MB", gb = "GB" }

    public struct Reading: Equatable, Sendable {
        public var amount: String
        public var unit: ByteUnit
    }

    private static let kilobyte: Int64 = 1024
    private static let megabyte: Int64 = 1024 * 1024
    private static let gigabyte: Int64 = 1024 * 1024 * 1024

    /// Rounded down, never up (`oneDecimal`).
    private static func oneDecimal(_ bytes: Int64, _ unit: Int64) -> String {
        let tenths = (bytes * 10) / unit
        return "\(tenths / 10).\(tenths % 10)"
    }

    /// A byte count as an amount and the unit it is in, or `nil` when nobody will say (`trafficReading`).
    public static func trafficReading(_ bytes: Int64?) -> Reading? {
        guard let bytes, bytes >= 0 else { return nil }
        if bytes < kilobyte { return Reading(amount: "\(bytes)", unit: .b) }
        if bytes < megabyte { return Reading(amount: "\(bytes / kilobyte)", unit: .kb) }
        if bytes < gigabyte { return Reading(amount: oneDecimal(bytes, megabyte), unit: .mb) }
        return Reading(amount: oneDecimal(bytes, gigabyte), unit: .gb)
    }

    /// How long ago a configuration last carried traffic, as something a screen can say out loud (`LastUsed.kt`).
    public enum LastUsed: Equatable, Sendable {
        case never
        case justNow
        case today(hour: Int, minute: Int)
        case yesterday
        case earlier(year: Int, month: Int, day: Int)
    }

    private static let justNowMillis: Int64 = 5 * 60 * 1000

    public static func lastUsed(_ lastUsedAt: Int64?, now: Int64, calendar: Calendar = .current) -> LastUsed {
        guard let lastUsedAt else { return .never }
        // A clock that moved backwards would otherwise produce a "last used" in the future, which has no bucket.
        if lastUsedAt > now { return .justNow }
        if now - lastUsedAt < justNowMillis { return .justNow }
        let then = Date(timeIntervalSince1970: Double(lastUsedAt) / 1000)
        let today = Date(timeIntervalSince1970: Double(now) / 1000)
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: then), to: calendar.startOfDay(for: today)).day ?? 0
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: then)
        if days == 0 { return .today(hour: c.hour ?? 0, minute: c.minute ?? 0) }
        if days == 1 { return .yesterday }
        return .earlier(year: c.year ?? 0, month: c.month ?? 0, day: c.day ?? 0)
    }

    private static let maxLabelLength = 20

    /// A scheme, an `@`, a bare host, an IP address or a UUID — the five shapes a name should never have.
    private static let looksLikeASecret = try! NSRegularExpression(
        pattern:
            #"(://)|(@)|([A-Za-z0-9-]+\.[A-Za-z]{2,})|(\d{1,3}(\.\d{1,3}){3})|([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"#
    )

    /// A configuration's name, made fit to sit where other software can read it (the Dock menu), or `nil`.
    public static func safeMenuLabel(_ name: String) -> String? {
        let collapsed = name.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        if collapsed.isEmpty { return nil }
        let whole = NSRange(collapsed.startIndex..., in: collapsed)
        if looksLikeASecret.firstMatch(in: collapsed, range: whole) != nil { return nil }
        if collapsed.count <= maxLabelLength { return collapsed }
        return String(collapsed.prefix(maxLabelLength - 1)).trimmingCharacters(in: .whitespaces) + "…"
    }
}

/// The order the menus offer connections in: the ones that carried traffic most recently first.
public func recentConnections(_ profiles: [ProfileSummary]) -> [ProfileSummary] {
    profiles.sorted { ($0.lastUsedAt ?? Int64.min) > ($1.lastUsedAt ?? Int64.min) }
}

/// What the configuration looks like in a preview: safe fields only. There is no field here that could hold a user
/// id, a key or a short id, so a preview card cannot draw one however it is written — the type is the guarantee.
public struct ConfigPreview: Equatable, Sendable {
    public var protocolLabel: String
    public var securityLabel: String?
    public var transportLabel: String
    public var server: String
    public var endpointHost: String
    public var endpointPort: Int
    public var serverName: String?
    public var fingerprint: String?
    public var flow: String?
    public var suggestedName: String
    public var mark: String
    public var keptParameters: [String]

    public init(draft: ProfileDraft) {
        let outbound = draft.outbound
        protocolLabel = Protocol_.label
        securityLabel = Self.securityLabel(outbound.security.kind)
        transportLabel = Self.transportLabel(outbound.transport.kind)
        server = "\(outbound.endpoint.host):\(outbound.endpoint.port)"
        endpointHost = outbound.endpoint.host
        endpointPort = outbound.endpoint.port
        serverName = outbound.security.serverName
        fingerprint = outbound.security.fingerprint
        flow = outbound.flow?.rawValue
        // A link with no fragment still has to be called something, and the host is what the user would call it.
        suggestedName = draft.suggestedName ?? outbound.endpoint.host
        mark = Protocol_.mark
        keptParameters = outbound.unknownParameters.map(\.name)
    }

    public static func securityLabel(_ kind: SecurityKind) -> String? {
        switch kind {
        case .none: return nil
        case .tls: return "TLS"
        case .reality: return "REALITY"
        }
    }

    public static func transportLabel(_ kind: TransportKind) -> String {
        switch kind {
        case .tcp: return "TCP"
        case .ws: return "WebSocket"
        case .grpc: return "gRPC"
        }
    }
}
