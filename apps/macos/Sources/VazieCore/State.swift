import Foundation

// Everything the interface may know. No field here can hold a secret: a credential never leaves the vault except
// through `revealCredential` and `exportLink`, one at a time.

public enum Failure: String, Equatable, Sendable {
    /// The Xray engine is not on disk (or failed its checksum).
    case engine
    case noInternet
    /// The stored profile could not be turned into something the engine can run.
    case profile
    /// The profile is valid and the engine cannot execute it. Reported before anything is started.
    case unsupported
    /// A local tunnel endpoint could not be opened.
    case tunnel
    /// The engine refused to start, or stopped on its own.
    case handshake
    /// The engine came up and nothing could reach the internet through it.
    case tunnelUnusable
    /// macOS would not let the app change the system proxy.
    case systemProxy
    /// Another VPN or proxy app sets the system proxy itself, so what this app wrote is not the one in effect.
    case proxyTaken
    /// The administrator's password was not given, so the tunnel for the whole Mac could not be opened.
    case adminDenied
    /// The tunnel interface could not be opened.
    case tun
    /// The interface is open and the Mac's traffic does not go through it.
    case tunBypassed
    /// The engine given to the administrator-level helper is not one it trusts.
    case integrity
    /// The Mac's way out changed or is gone while the tunnel was being made.
    case network
    /// The routes into the tunnel could not be made.
    case routes
    /// The Mac's DNS could not be pointed at the tunnel.
    case dns
    /// The kill switch could not be put in place, so the tunnel was not opened without it.
    case killSwitch
    case unknown
}

public enum ConnectionState: Equatable, Sendable {
    case idle
    case preparing
    case connecting(profileId: String)
    /// Confirmed: the tunnel was proven to carry a request after it was built, and has not failed a check since.
    case connected(profileId: String, since: Date)
    /// The tunnel was lost and is being brought back (`attempt` counts from 1).
    case reconnecting(profileId: String, attempt: Int)
    /// The tunnel is gone and nothing is bringing it back right now. `blocked` says the kill switch is holding the Mac's
    /// traffic (no internet, nothing leaks); otherwise traffic is going out directly, unprotected.
    case lost(profileId: String, blocked: Bool)
    case disconnecting
    case failed(Failure)
    case noInternet

    /// A tunnel is being built, is up, or is being torn down.
    public var isActive: Bool {
        switch self {
        case .preparing, .connecting, .connected, .reconnecting, .lost, .disconnecting: return true
        default: return false
        }
    }

    public var isConnected: Bool {
        if case .connected = self { return true }
        return false
    }

    /// Whether the Mac's traffic is, right now, going through the tunnel or held back from leaving. False while it
    /// would leave directly.
    public var isProtected: Bool {
        switch self {
        case .connected: return true
        case let .lost(_, blocked): return blocked
        default: return false
        }
    }

    public var profileId: String? {
        switch self {
        case let .connecting(id), let .connected(id, _), let .reconnecting(id, _), let .lost(id, _): return id
        default: return nil
        }
    }
}

public struct ConnectionSubject: Equatable, Sendable {
    public var id: String
    public var name: String
    public var protocolLabel: String
    public var mark: String

    public init(id: String, name: String, protocolLabel: String = Protocol_.label, mark: String = Protocol_.mark) {
        self.id = id
        self.name = name
        self.protocolLabel = protocolLabel
        self.mark = mark
    }
}

/// The closed set the connection details screen may render.
public struct Diagnostics: Equatable, Sendable {
    public var engine: String
    public var protocolId: String
    public var security: SecurityKind
    public var transport: String
    public var flow: XrayFlow?
    public var endpointHost: String
    public var endpointPort: Int
    /// Round-trip time to the server, measured from this machine; `nil` until it has been.
    public var latencyMs: Int?
    public var dnsMode: String
    public var ipv4: Bool
    public var ipv6: Bool

    public init(profile: StoredProfile) {
        let outbound = profile.outbound
        engine = "xray"
        protocolId = Protocol_.id
        security = outbound.security.kind
        transport = outbound.transport.kind == .tcp ? "raw" : outbound.transport.kind.rawValue
        flow = outbound.flow
        endpointHost = outbound.endpoint.host
        endpointPort = outbound.endpoint.port
        latencyMs = nil
        dnsMode = "tunnel"
        ipv4 = true
        // Names are resolved by the server, and the server decides what it can reach.
        ipv6 = true
    }
}

public struct Snapshot: Equatable, Sendable {
    public var state: ConnectionState
    public var subject: ConnectionSubject?
    public var diagnostics: Diagnostics?

    public static let idle = Snapshot(state: .idle, subject: nil, diagnostics: nil)

    public init(state: ConnectionState, subject: ConnectionSubject?, diagnostics: Diagnostics?) {
        self.state = state
        self.subject = subject
        self.diagnostics = diagnostics
    }
}

public struct Traffic: Equatable, Sendable {
    public var rx: Int64
    public var tx: Int64

    public init(rx: Int64, tx: Int64) {
        self.rx = rx
        self.tx = tx
    }
}

public struct ProfileSummary: Equatable, Sendable, Identifiable {
    public var id: String
    public var name: String
    public var protocolLabel: String
    public var mark: String
    public var createdAt: Int64
    public var lastUsedAt: Int64?
    /// Whether the engine can run it; `false` disables Connect instead of letting it fail.
    public var runnable: Bool

    public init(_ profile: StoredProfile) {
        id = profile.id
        name = profile.name
        protocolLabel = Protocol_.label
        mark = Protocol_.mark
        createdAt = profile.createdAt
        lastUsedAt = profile.lastUsedAt
        runnable = Support.isRunnable(profile.outbound)
    }
}

public struct ProfileDetails: Equatable, Sendable {
    public var id: String
    public var name: String
    public var engineLabel: String
    public var protocolLabel: String
    public var security: SecurityKind
    public var transport: TransportKind
    public var endpointHost: String
    public var endpointPort: Int
    public var serverName: String?
    public var fingerprint: String?
    public var flow: XrayFlow?
    /// The WebSocket path, the gRPC service name or the TCP header — whatever the transport made meaningful.
    public var transportDetail: String?
    public var unknownParameterNames: [String]
    public var createdAt: Int64
    public var lastUsedAt: Int64?

    public init(_ profile: StoredProfile) {
        let outbound = profile.outbound
        id = profile.id
        name = profile.name
        engineLabel = "xray"
        protocolLabel = Protocol_.label
        security = outbound.security.kind
        transport = outbound.transport.kind
        endpointHost = outbound.endpoint.host
        endpointPort = outbound.endpoint.port
        serverName = outbound.security.serverName
        fingerprint = outbound.security.fingerprint
        flow = outbound.flow
        switch outbound.transport {
        case let .tcp(header): transportDetail = header
        case let .ws(path, _): transportDetail = path
        case let .grpc(service, _): transportDetail = service.isEmpty ? nil : service
        }
        unknownParameterNames = outbound.unknownParameters.map(\.name)
        createdAt = profile.createdAt
        lastUsedAt = profile.lastUsedAt
    }
}

public enum Appearance: String, Codable, Sendable, CaseIterable {
    case nightIndigo = "night_indigo"
    case milk
}

public enum AppIconId: String, Codable, Sendable, CaseIterable {
    case orbit, lime, emerald, magenta
    case cottonCandy = "cotton_candy"
    case crimson, gold, pearl, onyx, monochrome
}

public enum AppIconPlate: String, Codable, Sendable, CaseIterable {
    case deep, light, ink, forest, rose, amber, mist
}

/// How the tunnel reaches the Mac's programs.
public enum ConnectionMode: String, Codable, Sendable, CaseIterable {
    /// A TUN interface: every program, whether or not it follows the system proxy. Needs the administrator's password.
    case wholeMac = "whole_mac"
    /// The system proxy: programs that follow it. No password.
    case systemProxy = "system_proxy"
}

public enum GuideId: String, Codable, Sendable, CaseIterable {
    case menuBar = "menu_bar"
    case dock
    case systemProxy = "system_proxy"
    case notification
}

public struct Prefs: Codable, Equatable, Sendable {
    public var appearance: Appearance = .nightIndigo
    public var appIcon: AppIconId = .orbit
    public var appIconPlate: AppIconPlate = .deep
    public var split: SplitTunnel = .off
    public var mode: ConnectionMode = .systemProxy
    /// Bring a lost connection back by itself.
    public var autoReconnect = true
    /// Block the Mac's traffic, instead of letting it out directly, when the whole-Mac tunnel is lost.
    public var killSwitch = false
    public var selectedId: String?
    public var onboardingDone = false
    public var tourDone = false
    public var viewedGuides: [GuideId] = []
    /// The notification explanation is shown once, at the first Connect.
    public var notificationPromptShown = false
    public var notificationsEnabled = false
    public var dismissedTips: [String] = []

    public init() {}

    /// A stored value is not a trusted enum: anything unknown resolves to the default.
    public static func sanitize(_ data: Data?) -> Prefs {
        guard let data, let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return Prefs() }
        var prefs = Prefs()
        prefs.appearance = (raw["appearance"] as? String).flatMap(Appearance.init(rawValue:)) ?? .nightIndigo
        prefs.appIcon = (raw["appIcon"] as? String).flatMap(AppIconId.init(rawValue:)) ?? .orbit
        let plates = Icons.plates(for: prefs.appIcon)
        if let plate = (raw["appIconPlate"] as? String).flatMap(AppIconPlate.init(rawValue:)), plates.contains(plate) {
            prefs.appIconPlate = plate
        } else {
            prefs.appIconPlate = Icons.signature(for: prefs.appIcon)
        }
        if let split = raw["split"] as? [String: Any] {
            let mode = SplitTunnel.mode(fromId: split["mode"] as? String)
            let sites = (split["sites"] as? [Any])?.compactMap { ($0 as? String).flatMap(SplitTunnel.normalizeSite) } ?? []
            prefs.split = SplitTunnel(mode: mode, sites: Array(Set(sites)).sorted().prefix(SplitTunnel.maxSites).map { $0 })
        }
        prefs.mode = (raw["mode"] as? String).flatMap(ConnectionMode.init(rawValue:)) ?? .systemProxy
        prefs.autoReconnect = raw["autoReconnect"] as? Bool ?? true
        prefs.killSwitch = raw["killSwitch"] as? Bool ?? false
        prefs.selectedId = raw["selectedId"] as? String
        prefs.onboardingDone = raw["onboardingDone"] as? Bool ?? false
        prefs.tourDone = raw["tourDone"] as? Bool ?? false
        prefs.viewedGuides = ((raw["viewedGuides"] as? [Any]) ?? []).compactMap { ($0 as? String).flatMap(GuideId.init(rawValue:)) }
        prefs.notificationPromptShown = raw["notificationPromptShown"] as? Bool ?? false
        prefs.notificationsEnabled = raw["notificationsEnabled"] as? Bool ?? false
        prefs.dismissedTips = Array((((raw["dismissedTips"] as? [Any]) ?? []).compactMap { $0 as? String }).prefix(50))
        return prefs
    }
}

/// What the app knows about the Xray engine on disk.
public enum EngineStatus: Equatable, Sendable {
    case unknown
    case missing
    case installing(done: Int64, total: Int64)
    case ready(version: String)
}

/// The words for each of the engine's install failures (`InstallError`), never the engine's own output.
public enum EngineInstallFailure: String, Equatable, Sendable {
    case unsupportedPlatform = "unsupported_platform"
    case download, checksum, archive, disk
}
