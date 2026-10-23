import Foundation

// What the Vazie backend publishes to every client without a session: the About links and the questions. Every
// field is untrusted and read defensively, as in `VazieLinksSource.kt` and `VazieFaqSource.kt`: an item the app
// cannot show is dropped on its own and the rest stay.

public enum LinkSection: String, CaseIterable, Sendable {
    case author = "AUTHOR", project = "PROJECT", security = "SECURITY", help = "HELP", support = "SUPPORT", feedback = "FEEDBACK"
}

public struct Localized: Equatable, Sendable {
    public var ru: String
    public var en: String

    public func resolve(_ language: Language) -> String { language == .ru ? ru : en }
}

public struct AppLink: Equatable, Sendable {
    public var key: String
    public var section: LinkSection
    public var url: String
    public var title: Localized
    public var subtitle: Localized?
    public var action: Localized?
}

public struct AppLinks: Equatable, Sendable {
    public var links: [AppLink]

    public func links(in section: LinkSection) -> [AppLink] { links.filter { $0.section == section } }
    public var support: AppLink? { links.first { $0.url.hasPrefix("mailto:") } }
}

private let urlMaxLength = 512

private func hasControl(_ text: String) -> Bool {
    text.unicodeScalars.contains { ($0.value <= 0x1F) || (0x7F...0x9F).contains($0.value) }
}

private func matches(_ pattern: String, _ text: String) -> Bool {
    text.range(of: pattern, options: .regularExpression) != nil
}

public enum Backend {
    /// Whether `url` is something the app will open: `https` with a host and no user info, or one `mailto:`.
    public static func isOpenableLink(_ url: String) -> Bool {
        url.count <= urlMaxLength && !hasControl(url)
            && (matches(#"^https://[^/?#@\s]+([/?#]\S*)?$"#, url) || matches(#"^mailto:[^\s@?/]+@[^\s@?/]+\.[^\s@?/]+$"#, url))
    }

    /// What the screen will open: `https`, or a path on the site that is not `//host`.
    public static func isOpenableFaqUrl(_ url: String) -> Bool {
        if url.count > urlMaxLength || url.hasPrefix("//") { return false }
        if url.hasPrefix("/") { return matches(#"^/[A-Za-z0-9._~/-]*(#[A-Za-z0-9_-]+)?$"#, url) }
        return url.hasPrefix("https://") && isOpenableLink(url)
    }

    private static func linkText(_ value: Any?) -> Localized? {
        guard let raw = value as? [String: Any] else { return nil }
        func usable(_ t: String?) -> String? {
            guard let t, !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, t.count <= 400, !hasControl(t) else { return nil }
            return t
        }
        guard let ru = usable(raw["ru"] as? String), let en = usable(raw["en"] as? String) else { return nil }
        return Localized(ru: ru, en: en)
    }

    public static func readLinks(_ json: Any?) -> AppLinks? {
        guard let list = (json as? [String: Any])?["links"] as? [Any] else { return nil }
        var links: [AppLink] = []
        for item in list {
            guard let raw = item as? [String: Any],
                let section = (raw["section"] as? String).flatMap(LinkSection.init(rawValue:)),
                let url = raw["url"] as? String, isOpenableLink(url),
                let key = raw["key"] as? String, !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                let title = linkText(raw["title"])
            else { continue }
            links.append(
                AppLink(
                    key: key, section: section, url: url, title: title,
                    subtitle: raw["subtitle"] == nil || raw["subtitle"] is NSNull ? nil : linkText(raw["subtitle"]),
                    action: raw["action"] == nil || raw["action"] is NSNull ? nil : linkText(raw["action"])))
        }
        return links.isEmpty ? nil : AppLinks(links: links)
    }

    // -- FAQ --------------------------------------------------------------------------------------------

    public struct FaqText: Equatable, Sendable {
        public var ru: String
        public var en: String?

        public func resolve(_ language: Language) -> String { language == .ru ? ru : (en ?? ru) }
    }

    public struct FaqLink: Equatable, Sendable {
        public var url: String
        public var label: FaqText
    }

    public struct FaqItem: Equatable, Sendable {
        public var key: String
        public var question: FaqText
        public var answer: FaqText
        public var link: FaqLink?
    }

    public struct Faq: Equatable, Sendable {
        public var items: [FaqItem]
    }

    private static func faqText(_ value: Any?, max: Int, multiline: Bool) -> FaqText? {
        guard let raw = value as? [String: Any] else { return nil }
        func usable(_ t: String?) -> String? {
            guard let t, !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, t.count <= max else { return nil }
            let stripped = multiline ? t.replacingOccurrences(of: "\n", with: "") : t
            return hasControl(stripped) ? nil : t
        }
        guard let ru = usable(raw["ru"] as? String) else { return nil }
        return FaqText(ru: ru, en: usable(raw["en"] as? String))
    }

    public static func readFaq(_ json: Any?) -> Faq? {
        guard let list = (json as? [String: Any])?["items"] as? [Any] else { return nil }
        var items: [FaqItem] = []
        for item in list {
            guard let raw = item as? [String: Any], let key = raw["key"] as? String,
                !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                let question = faqText(raw["question"], max: 200, multiline: false),
                let answer = faqText(raw["answer"], max: 2000, multiline: true)
            else { continue }
            var link: FaqLink?
            if let rawLink = raw["link"] as? [String: Any], let url = rawLink["url"] as? String, isOpenableFaqUrl(url),
                let label = faqText(rawLink["label"], max: 80, multiline: false)
            {
                link = FaqLink(url: url, label: label)
            }
            items.append(FaqItem(key: key, question: question, answer: answer, link: link))
        }
        // An empty list is the operator's own answer (every question switched off) and is believed; questions none
        // of which can be shown are not.
        return !list.isEmpty && items.isEmpty ? nil : Faq(items: items)
    }
}
