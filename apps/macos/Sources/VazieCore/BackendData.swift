import Foundation
import Network

public enum Config {
    /// Where the Vazie backend answers: the public links, questions and nothing else. A build may point elsewhere
    /// with `VazieAPIURL` in its Info.plist, as the Android build does with `vazie.api.url`. The official address is not in
    /// the repository (`scripts/build-app.sh` writes it from `VAZIE_API_URL`); without one the placeholder answers nothing.
    public static var apiURL: String { plist("VazieAPIURL") ?? "https://api.example.com/v1" }

    /// The Vazie site, for the questions that point at a page on it.
    public static var siteURL: String { plist("VazieSiteURL") ?? "https://example.com" }

    public static var version: String { plist("CFBundleShortVersionString") ?? "1.0.0" }
    public static var build: String { plist("CFBundleVersion") ?? "1" }

    private static func plist(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String, !value.isEmpty else { return nil }
        return value
    }
}

/// The last list the backend published, kept so a cold start offline shows it too (`AppLinksRepository.kt`,
/// `FaqRepository.kt`). The app has no questions and no links of its own: they are `nil` until the backend has
/// answered once.
public final class PublishedDocument<Value: Sendable>: @unchecked Sendable {
    private static var timeout: TimeInterval { 8 }
    /// The backend caches its answer for five minutes; asking more often than this learns nothing.
    private static var minInterval: TimeInterval { 10 * 60 }

    private let cacheURL: URL
    private let path: String
    private let read: @Sendable (Any?) -> Value?
    /// The FAQ asks every time while it has nothing, so "try again" works.
    private let askWhileEmpty: Bool
    private let session: URLSession
    private let lock = NSLock()
    private var loaded = false
    private var lastAsked: Date?
    private var inflight: Task<Void, Never>?
    public private(set) var value: Value?
    public private(set) var loading = false

    public init(
        cache: URL, path: String, askWhileEmpty: Bool, session: URLSession? = nil, read: @escaping @Sendable (Any?) -> Value?
    ) {
        cacheURL = cache
        self.path = path
        self.read = read
        self.askWhileEmpty = askWhileEmpty
        if let session {
            self.session = session
        } else {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.httpShouldSetCookies = false
            configuration.httpCookieAcceptPolicy = .never
            configuration.urlCache = nil
            configuration.timeoutIntervalForRequest = Self.timeout
            self.session = URLSession(configuration: configuration)
        }
    }

    private func loadFromDisk() {
        lock.lock()
        defer { lock.unlock() }
        if loaded { return }
        loaded = true
        // Read back through the same checks: an edited file opens nothing new.
        if let data = try? Data(contentsOf: cacheURL), let json = try? JSONSerialization.jsonObject(with: data) {
            value = read(json)
        }
    }

    public func current() -> Value? {
        loadFromDisk()
        return value
    }

    /// Asks the backend, unless it was asked a moment ago. `changed` is called when `value` or `loading` moved.
    public func refresh(force: Bool = false, changed: @escaping @Sendable () -> Void = {}) async {
        if let inflight { return await inflight.value }
        let task = Task { await run(force: force, changed: changed) }
        inflight = task
        await task.value
        inflight = nil
    }

    private func run(force: Bool, changed: @Sendable () -> Void) async {
        loadFromDisk()
        let recent = lastAsked.map { Date().timeIntervalSince($0) < Self.minInterval } ?? false
        if !force && recent && (value != nil || !askWhileEmpty) { return }
        lastAsked = Date()
        loading = true
        changed()
        var json: Any?
        var raw: Data?
        do {
            // No session, no cookies, nothing of the person's in it.
            guard let url = URL(string: Config.apiURL + path) else { throw URLError(.badURL) }
            var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: Self.timeout)
            request.httpShouldHandleCookies = false
            let (data, response) = try await session.data(for: request)
            if let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) {
                json = try? JSONSerialization.jsonObject(with: data)
                raw = data
            }
        } catch {
            json = nil
        }
        loading = false
        if let json, let fetched = read(json), let raw {
            let different = !(value.map { "\($0)" } == "\(fetched)")
            if different {
                value = fetched
                try? FileManager.default.createDirectory(at: cacheURL.deletingLastPathComponent(), withIntermediateDirectories: true)
                // Losing the file costs the list on the next cold start offline, nothing more.
                try? raw.write(to: cacheURL, options: .atomic)
            }
        }
        changed()
    }
}

/// Whether the Mac has a network at all, as the system reports it.
public final class NetworkMonitor: @unchecked Sendable {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "app.vazie.vpn.network")
    private let lock = NSLock()
    private var current = true

    public init() {}

    public var isOnline: Bool { lock.lock(); defer { lock.unlock() }; return current }

    /// `handler` is called on every change of whether any network is usable.
    public func start(_ handler: @escaping @Sendable (Bool) -> Void) {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let online = path.status == .satisfied
            self.lock.lock()
            let changed = self.current != online
            self.current = online
            self.lock.unlock()
            if changed { handler(online) }
        }
        monitor.start(queue: queue)
    }

    public func stop() { monitor.cancel() }
}
