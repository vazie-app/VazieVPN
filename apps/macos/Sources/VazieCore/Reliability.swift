import AppKit
import Foundation
import Network

// What keeps a connection honest over time: when to try again, what the Mac says about its network, and whether a tunnel that
// is "up" really carries traffic.

/// How long to wait before the Nth attempt to bring a lost tunnel back, and when to stop trying by itself.
///
/// Doubling, capped, with a little spread so that a crowd of clients does not return to a server in the same second. After
/// `maxAttempts` the app stops and waits for the person, or for something that changes the situation (the network coming
/// back, the Mac waking): a connection that cannot be had is not retried forever at the server's expense.
public struct ReconnectPolicy: Equatable, Sendable {
    public var base: TimeInterval
    public var cap: TimeInterval
    public var maxAttempts: Int
    /// 0.2 spreads a delay over ±20 %.
    public var spread: Double

    public init(base: TimeInterval = 1, cap: TimeInterval = 30, maxAttempts: Int = 8, spread: Double = 0.2) {
        self.base = base
        self.cap = cap
        self.maxAttempts = maxAttempts
        self.spread = spread
    }

    public static let standard = ReconnectPolicy()

    /// `unit` is a number in 0…1 (a random one in production), so that the result is testable.
    public func delay(forAttempt attempt: Int, unit: Double = 0.5) -> TimeInterval {
        let steps = min(max(attempt - 1, 0), 30)
        let raw = min(cap, base * pow(2, Double(steps)))
        let factor = 1 + spread * (2 * min(max(unit, 0), 1) - 1)
        return min(cap, max(0, raw * factor))
    }
}

/// What the Mac's network looks like, reduced to what decides whether a tunnel built on it still stands.
public struct NetworkPathSignature: Equatable, Sendable {
    public var online: Bool
    /// The hardware interface the default route leaves by, and its gateway.
    public var interface: String?
    public var gateway: String?

    public init(online: Bool, interface: String?, gateway: String?) {
        self.online = online
        self.interface = interface
        self.gateway = gateway
    }
}

public enum SystemEvent: Equatable, Sendable {
    case willSleep
    case didWake
    case path(NetworkPathSignature)
}

public protocol SystemEventSource: AnyObject, Sendable {
    func start(_ handler: @escaping @Sendable (SystemEvent) -> Void)
    func stop()
}

/// The system's own notifications: `NWPathMonitor` for the network (read again through the routing table, so that a change of
/// gateway or of Wi-Fi to Ethernet is seen and not only "online" or "offline"), and the workspace's sleep and wake.
public final class MacSystemEvents: SystemEventSource, @unchecked Sendable {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "app.vazie.vpn.events")
    private let lock = NSLock()
    private var handler: (@Sendable (SystemEvent) -> Void)?
    private var pending: DispatchWorkItem?
    private var last: NetworkPathSignature?
    private var observers: [NSObjectProtocol] = []
    private let debounce: TimeInterval

    public init(debounce: TimeInterval = 1.0) { self.debounce = debounce }

    public func start(_ handler: @escaping @Sendable (SystemEvent) -> Void) {
        lock.lock(); self.handler = handler; lock.unlock()
        monitor.pathUpdateHandler = { [weak self] path in self?.schedule(online: path.status == .satisfied) }
        monitor.start(queue: queue)
        let center = NSWorkspace.shared.notificationCenter
        observers = [
            center.addObserver(forName: NSWorkspace.willSleepNotification, object: nil, queue: nil) { [weak self] _ in self?.emit(.willSleep) },
            center.addObserver(forName: NSWorkspace.didWakeNotification, object: nil, queue: nil) { [weak self] _ in self?.emit(.didWake) },
        ]
    }

    public func stop() {
        monitor.cancel()
        pending?.cancel()
        for observer in observers { NSWorkspace.shared.notificationCenter.removeObserver(observer) }
        observers = []
        lock.lock(); handler = nil; lock.unlock()
    }

    private func emit(_ event: SystemEvent) {
        lock.lock(); let handler = handler; lock.unlock()
        handler?(event)
    }

    /// A Wi-Fi that flaps raises a dozen updates in a second; one reading is taken when it settles.
    private func schedule(online: Bool) {
        pending?.cancel()
        let item = DispatchWorkItem { [weak self] in
            Task {
                let route = online ? await PhysicalRoute.current() : nil
                self?.publish(NetworkPathSignature(online: online, interface: route?.interface, gateway: route?.gateway))
            }
        }
        pending = item
        queue.asyncAfter(deadline: .now() + debounce, execute: item)
    }

    private func publish(_ signature: NetworkPathSignature) {
        lock.lock()
        let changed = last != signature
        last = signature
        lock.unlock()
        if changed { emit(.path(signature)) }
    }
}

/// The verdict of one look at a running tunnel.
public enum Health: Equatable, Sendable {
    case ok
    /// A check failed that may be a hiccup: it counts, and a second one in a row is a loss.
    case degraded(String)
    /// The tunnel is gone or cannot be carrying anything.
    case dead(String)
}

public protocol TunnelHealthChecking: Sendable {
    func check(_ tunnel: Tunnel) async -> Health
}

/// The production check. In order, cheapest first: is the supervisor/engine there (a file and a signal), do the Mac's packets
/// for the internet go to the tunnel's interface (one `route get`), and does the engine reach the internet through the server
/// (one tiny request, skipped when the engine's own byte counters show traffic coming back since the last look — real use is
/// the best proof and costs nothing).
public final class LiveHealth: TunnelHealthChecking, @unchecked Sendable {
    private let lock = NSLock()
    private var lastReceived: Int64 = -1

    public init() {}

    /// Whether bytes have come back since the last look.
    private func recordMoved(_ received: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        let moved = lastReceived >= 0 && received > lastReceived
        lastReceived = received
        return moved
    }

    public func check(_ tunnel: Tunnel) async -> Health {
        if let restartable = tunnel as? RestartableTunnel {
            guard restartable.supervisorIsAlive else { return .dead("supervisor") }
            if restartable.trafficIsHeld { return .dead("engine") }
            if let session = restartable as? TunSession {
                switch session.currentStatus().kind {
                case .lostBlocked, .lostOpen: return .dead("engine")
                // The kill switch lapsed and could not be put back, or the helper reports some other failure: rebuild.
                case .failed: return .dead("helper")
                default: break
                }
                if !(await session.routesIntact()) { return .dead("routes") }
            }
        }
        if let now = await tunnel.traffic(), recordMoved(now.rx) { return .ok }
        let session = Probe.proxied(httpPort: tunnel.httpPort)
        defer { session.invalidateAndCancel() }
        do {
            _ = try await session.data(from: URL(string: "https://cp.cloudflare.com/generate_204")!)
            return .ok
        } catch {
            return .degraded("probe")
        }
    }
}

/// Settings the engine reads when it starts and the person can change at any time. Shared with the engine through a lock, as
/// the engine is created before the model that owns the preferences.
public final class RuntimeOptions: @unchecked Sendable {
    private let lock = NSLock()
    private var kill = false

    public init() {}

    public var killSwitch: Bool {
        get { lock.lock(); defer { lock.unlock() }; return kill }
        set { lock.lock(); kill = newValue; lock.unlock() }
    }
}

extension Failure {
    public init(_ error: TunnelStartError) {
        switch error {
        case .engine: self = .engine
        case .config: self = .profile
        case .bind: self = .tunnel
        case .timeout: self = .handshake
        case .cancelled: self = .adminDenied
        case .privilege, .tun: self = .tun
        case .bypassed: self = .tunBypassed
        case .integrity: self = .integrity
        case .network: self = .network
        case .routes: self = .routes
        case .dns: self = .dns
        case .killSwitch: self = .killSwitch
        }
    }

    /// Whether trying again by itself can help. A missing engine, a configuration it cannot run, a password not given or
    /// another VPN in the way will be exactly as they were a second from now.
    public var isRetriable: Bool {
        switch self {
        case .handshake, .tunnel, .tunnelUnusable, .network, .routes, .dns, .tunBypassed, .noInternet, .unknown: return true
        case .engine, .profile, .unsupported, .systemProxy, .proxyTaken, .adminDenied, .tun, .integrity, .killSwitch: return false
        }
    }
}
