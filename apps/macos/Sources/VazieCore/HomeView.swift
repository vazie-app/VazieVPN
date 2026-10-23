import Foundation

// How Home's state is drawn (`HomePresentation.kt`, `HomeRoutePresentation.kt`, `ConnectionPresentation.kt`) —
// pure, so the whole vocabulary is one test away.

public enum HomeView: Equatable {
    case empty
    case unselected
    case ready(name: String, mark: String, protocolLabel: String, connection: ConnectionState, runnable: Bool)

    /// The tunnel's profile outranks the selection: the screen describes what exists before it describes what was
    /// chosen.
    public static func make(snapshot: Snapshot, profiles: [ProfileSummary], selectedId: String?) -> HomeView {
        let subject: (id: String, name: String, mark: String, protocolLabel: String)? =
            snapshot.subject.map { ($0.id, $0.name, $0.mark, $0.protocolLabel) }
            ?? profiles.first(where: { $0.id == selectedId }).map { ($0.id, $0.name, $0.mark, $0.protocolLabel) }
        guard let subject else { return profiles.isEmpty ? .empty : .unselected }
        let stored = profiles.first { $0.id == subject.id }
        return .ready(
            name: stored?.name ?? subject.name, mark: subject.mark, protocolLabel: subject.protocolLabel,
            connection: snapshot.state, runnable: stored?.runnable ?? true)
    }
}

public enum Tone: Sendable { case neutral, info, warning, success, error }
public enum HomeAction: Sendable { case connect, cancel, disconnect, retry, check, add }
public enum RouteState: Sendable { case disconnected, connecting, connected, failed }
public enum ActionVariant: Sendable { case primary, secondary, destructive }

public struct Presentation: Equatable {
    public enum Line: Equatable {
        case words(key: String)
        case wordsNamed(key: String)
        case session(protocolLabel: String)
    }

    /// The one-word status.
    public var statusKey: String
    /// The headline.
    public var titleKey: String?
    public var tone: Tone
    public var action: HomeAction
    public var actionKey: String
    public var actionVariant: ActionVariant
    public var actionEnabled: Bool
    public var route: RouteState?
    public var line: Line
}

public func failureKey(_ failure: Failure) -> String {
    switch failure {
    case .engine: return "mac_failure_engine"
    case .systemProxy: return "mac_failure_system_proxy"
    case .proxyTaken: return "mac_failure_proxy_taken"
    case .adminDenied: return "mac_failure_admin_denied"
    case .tun: return "mac_failure_tun"
    case .tunBypassed: return "mac_failure_tun_bypassed"
    case .integrity: return "mac_failure_integrity"
    case .network: return "mac_failure_network"
    case .routes: return "mac_failure_routes"
    case .dns: return "mac_failure_dns"
    case .killSwitch: return "mac_failure_kill_switch"
    case .noInternet: return "home_failure_no_internet"
    case .profile: return "home_failure_profile"
    case .unsupported: return "home_failure_unsupported"
    case .tunnel: return "home_failure_tunnel"
    case .handshake: return "home_failure_handshake"
    case .tunnelUnusable: return "home_failure_tunnel_unusable"
    case .unknown: return "home_failure_unknown"
    }
}

public func present(_ view: HomeView) -> Presentation {
    switch view {
    case .empty:
        return Presentation(
            statusKey: "home_status_no_configuration", titleKey: "home_no_config_title", tone: .neutral, action: .add,
            actionKey: "home_add_config", actionVariant: .primary, actionEnabled: true, route: nil,
            line: .words(key: "home_no_config_body"))
    case .unselected:
        return Presentation(
            statusKey: "home_status_unselected", titleKey: "home_unselected_title", tone: .neutral, action: .connect,
            actionKey: "home_action_connect", actionVariant: .primary, actionEnabled: false, route: nil,
            line: .words(key: "home_unselected_body"))
    case let .ready(_, _, protocolLabel, connection, runnable):
        switch connection {
        case .idle:
            return Presentation(
                statusKey: "home_status_disconnected", titleKey: "home_route_title_disconnected", tone: .neutral,
                action: .connect, actionKey: "home_action_connect", actionVariant: .primary,
                // A configuration this build cannot run is still the person's configuration and stays visible and
                // named; what it does not get is a button that would fail.
                actionEnabled: runnable, route: .disconnected,
                line: .words(key: runnable ? "home_route_subtitle_direct" : "home_subtitle_unsupported"))
        case .preparing:
            return Presentation(
                statusKey: "home_status_preparing", titleKey: "home_route_title_connecting", tone: .info, action: .cancel,
                actionKey: "home_action_cancel", actionVariant: .secondary, actionEnabled: true, route: .connecting,
                line: .words(key: "mac_subtitle_preparing"))
        case .connecting:
            return Presentation(
                statusKey: "home_status_connecting", titleKey: "home_route_title_connecting", tone: .warning, action: .cancel,
                actionKey: "home_action_cancel", actionVariant: .secondary, actionEnabled: true, route: .connecting,
                line: .wordsNamed(key: "home_route_subtitle_connecting"))
        case .connected:
            return Presentation(
                statusKey: "home_status_protected", titleKey: "home_route_title_connected", tone: .success,
                action: .disconnect, actionKey: "home_action_disconnect", actionVariant: .destructive,
                actionEnabled: true, route: .connected, line: .session(protocolLabel: protocolLabel))
        case .reconnecting:
            return Presentation(
                statusKey: "mac_status_reconnecting", titleKey: "mac_title_reconnecting", tone: .warning, action: .cancel,
                actionKey: "home_action_cancel", actionVariant: .secondary, actionEnabled: true, route: .connecting,
                line: .words(key: "mac_subtitle_reconnecting"))
        case let .lost(_, blocked):
            return Presentation(
                statusKey: blocked ? "mac_status_lost_blocked" : "mac_status_lost_open", titleKey: "mac_title_lost", tone: .error,
                action: .retry, actionKey: "mac_action_reconnect", actionVariant: .primary, actionEnabled: runnable, route: .failed,
                line: .words(key: blocked ? "mac_lost_blocked" : "mac_lost_open"))
        case .disconnecting:
            return Presentation(
                statusKey: "home_status_disconnecting", titleKey: "home_route_title_disconnecting", tone: .warning,
                action: .cancel, actionKey: "home_action_disconnecting", actionVariant: .secondary, actionEnabled: false,
                route: .disconnected, line: .words(key: "home_title_disconnecting"))
        case let .failed(failure):
            return Presentation(
                statusKey: "home_status_failed", titleKey: "home_title_failed", tone: .error, action: .retry,
                actionKey: "home_action_retry", actionVariant: .primary, actionEnabled: runnable, route: .failed,
                line: .words(key: failureKey(failure)))
        case .noInternet:
            return Presentation(
                statusKey: "home_status_no_internet", titleKey: "home_status_no_internet", tone: .neutral, action: .check,
                actionKey: "home_action_check_connection", actionVariant: .secondary, actionEnabled: true, route: .failed,
                line: .words(key: "home_subtitle_no_internet"))
        }
    }
}
