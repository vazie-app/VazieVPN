import Foundation

/// An `https` address whose answer is share links (`SubscriptionLink.kt`).
public struct SubscriptionLink: Equatable, Sendable {
    /// The whole address. It carries the subscriber's token, so it is a credential like any share link.
    public var url: String
    /// The only part of the address a screen may show.
    public var host: String

    private static let scheme = "https://"

    public static func parse(_ raw: String) -> SubscriptionLink? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.lowercased().hasPrefix(scheme) else { return nil }
        // One address and nothing around it.
        if text.contains(where: { $0.isWhitespace }) { return nil }

        let afterScheme = String(text.dropFirst(scheme.count))
        let cut = afterScheme.firstIndex(where: { "/?#".contains($0) })
        let authority = cut.map { String(afterScheme[..<$0]) } ?? afterScheme
        // `user:password@host` is how a link dresses one server up as another.
        if authority.contains("@") { return nil }

        var host: String
        if authority.hasPrefix("[") {
            guard let close = authority.firstIndex(of: "]") else { return nil }
            host = String(authority[...close])
        } else if let colon = authority.lastIndex(of: ":") {
            host = String(authority[..<colon])
        } else {
            host = authority
        }
        let isV6 = host.hasPrefix("[") && host.hasSuffix("]") && isIPv6Literal(String(host.dropFirst().dropLast()))
        guard isPlainHost(host) || isV6 else { return nil }
        let rest = String(authority.dropFirst(host.count))
        if !rest.isEmpty {
            guard rest.hasPrefix(":"), (1...5).contains(rest.count - 1), rest.dropFirst().allSatisfy(\.isASCII), rest.dropFirst().allSatisfy(\.isNumber)
            else { return nil }
        }
        return SubscriptionLink(url: text, host: host.lowercased())
    }

    /// Whether `raw` is a web address without TLS: never asked for over a connection anyone on the path can read.
    public static func isInsecure(_ raw: String) -> Bool {
        raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("http://")
    }
}

public struct SubscriptionContent: Equatable, Sendable {
    public var recognized: [ProfileDraft]
    public var skipped: Int
    public var truncated: Bool
}

public enum Subscription {
    /// More than anyone picks a server from, and few enough to store and show at once.
    public static let maxConfigurations = 100
    private static let linkMark = "://"

    /// The list as text. Links in the clear are taken as they are; anything else is tried as Base64, in either
    /// alphabet and with or without padding, because servers disagree on all three.
    static func decoded(_ body: String) -> String {
        if body.contains(linkMark) { return body }
        var compact = String(body.filter { !$0.isWhitespace })
        while compact.hasSuffix("=") { compact.removeLast() }
        compact = compact.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while compact.count % 4 != 0 { compact += "=" }
        guard let data = Data(base64Encoded: compact) else { return body }
        return String(decoding: data, as: UTF8.self)
    }

    /// Reads a subscription's answer: share links one per line, or Base64-encoded as a whole.
    public static func read(_ body: String) -> SubscriptionContent {
        var recognized: [ProfileDraft] = []
        var skipped = 0
        var truncated = false
        let lines = decoded(body).split(omittingEmptySubsequences: false, whereSeparator: { $0 == "\n" || $0 == "\r\n" || $0 == "\r" })
        for rawLine in lines {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            // Only something link-shaped is a link Vazie failed to read; a blank line or an error page is not.
            if !line.contains(linkMark) { continue }
            if recognized.count == maxConfigurations {
                truncated = true
                break
            }
            if case let .recognized(draft) = Parsers.parse(line) { recognized.append(draft) } else { skipped += 1 }
        }
        return SubscriptionContent(recognized: recognized, skipped: skipped, truncated: truncated)
    }
}

public enum SubscriptionFailure: String, Equatable, Sendable {
    case insecure, unreachable, refused, tooLarge, empty
}

public enum SubscriptionFetchResult: Sendable {
    case list(token: String, host: String, configs: [ConfigPreview], skipped: Int, truncated: Bool)
    case failed(host: String?, reason: SubscriptionFailure)
}

/// A subscription link: one `GET` to the link's own address, once. The request goes to that server and nowhere
/// else, with no session and no cookies (`HttpSubscriptionSource.kt`).
public actor SubscriptionFetcher {
    private static let bodyLimit = 1024 * 1024
    private static let timeout: TimeInterval = 20
    private static let tokenLifetime: TimeInterval = 10 * 60
    static let userAgent = "VazieVPN"

    private struct Held {
        var drafts: [(draft: ProfileDraft, name: String)]
        var expires: Date
    }

    private var held: [String: Held] = [:]
    private let session: URLSession

    public init(session: URLSession? = nil) {
        if let session {
            self.session = session
        } else {
            // No cookies, no cache, no credentials: the list is the only thing asked for.
            let configuration = URLSessionConfiguration.ephemeral
            configuration.httpShouldSetCookies = false
            configuration.httpCookieAcceptPolicy = .never
            configuration.urlCache = nil
            configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
            configuration.timeoutIntervalForRequest = Self.timeout
            self.session = URLSession(configuration: configuration)
        }
    }

    public func fetch(_ rawLink: String) async -> SubscriptionFetchResult {
        if SubscriptionLink.isInsecure(rawLink) { return .failed(host: nil, reason: .insecure) }
        guard let link = SubscriptionLink.parse(rawLink), let url = URL(string: link.url) else {
            return .failed(host: nil, reason: .refused)
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: Self.timeout)
        request.httpMethod = "GET"
        request.setValue("text/plain, */*;q=0.5", forHTTPHeaderField: "Accept")
        // Panels pick the format by what asks; the app's own name is what it sends.
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false

        do {
            let (bytes, response) = try await session.bytes(for: request)
            // The link was checked to be https; a redirect must not quietly undo that.
            guard let http = response as? HTTPURLResponse, http.url?.scheme?.lowercased() == "https" else {
                return .failed(host: link.host, reason: .unreachable)
            }
            guard (200..<300).contains(http.statusCode) else { return .failed(host: link.host, reason: .refused) }
            var data = Data()
            for try await byte in bytes {
                data.append(byte)
                if data.count > Self.bodyLimit { return .failed(host: link.host, reason: .tooLarge) }
            }
            let content = Subscription.read(String(decoding: data, as: UTF8.self))
            if content.recognized.isEmpty { return .failed(host: link.host, reason: .empty) }

            let token = UUID().uuidString
            sweep()
            held[token] = Held(
                drafts: content.recognized.map { ($0, ConfigPreview(draft: $0).suggestedName) },
                expires: Date().addingTimeInterval(Self.tokenLifetime))
            return .list(
                token: token, host: link.host, configs: content.recognized.map(ConfigPreview.init(draft:)),
                skipped: content.skipped, truncated: content.truncated)
        } catch {
            // Engines report an unreachable host each in their own way; the person is told one thing.
            return .failed(host: link.host, reason: .unreachable)
        }
    }

    private func sweep() {
        let now = Date()
        held = held.filter { $0.value.expires > now }
    }

    /// The drafts behind a token, once.
    public func take(_ token: String) -> [(draft: ProfileDraft, name: String)]? {
        guard let entry = held.removeValue(forKey: token), entry.expires > Date() else { return nil }
        return entry.drafts
    }

    public func discard(_ token: String) { held[token] = nil }
}
