import Foundation

public enum Language: String, Sendable {
    case en, ru

    /// Russian for a Russian-speaking Mac, English for every other.
    public static func of(preferred: [String] = Locale.preferredLanguages) -> Language {
        (preferred.first ?? "en").lowercased().hasPrefix("ru") ? .ru : .en
    }
}

/// The Android app's words first, the extension's own over them, the Mac's over both: where a sentence differs (a
/// phone's "widgets" are a Mac's "menu bar"), the later table wins. A missing key shows English, then the key
/// itself: never a blank.
public struct Translator: Sendable {
    public let language: Language
    private let table: [String: Entry]
    private let english: [String: Entry]

    enum Entry: Sendable {
        case text(String)
        case plural([String: String])
    }

    public init(language: Language) {
        self.language = language
        self.table = Self.tables[language] ?? [:]
        self.english = Self.tables[.en] ?? [:]
    }

    private static let tables: [Language: [String: Entry]] = {
        var result: [Language: [String: Entry]] = [:]
        for language in [Language.en, .ru] {
            var merged: [String: Entry] = [:]
            // The extension's words are about a browser, a helper and a toolbar; only those the Mac still means are
            // taken (`ExtensionWords.keys`), the rest would speak of things this app does not have.
            for (file, keep) in [("android", nil), ("ext", ExtensionWords.keys), ("mac", nil)] as [(String, Set<String>?)] {
                for (key, entry) in load("\(file).\(language.rawValue)") where keep == nil || keep!.contains(key) {
                    merged[key] = entry
                }
            }
            result[language] = merged
        }
        return result
    }()

    private static func load(_ name: String) -> [String: Entry] {
        guard let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "i18n"),
            let data = try? Data(contentsOf: url),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return [:] }
        var out: [String: Entry] = [:]
        for (key, value) in json {
            if let text = value as? String {
                out[key] = .text(text)
            } else if let forms = value as? [String: String] {
                out[key] = .plural(forms)
            }
        }
        return out
    }

    public static func allKeys(_ language: Language) -> Set<String> { Set((tables[language] ?? [:]).keys) }

    private func lookup(_ key: String) -> Entry? { table[key] ?? english[key] }

    public func has(_ key: String) -> Bool { table[key] != nil || english[key] != nil }

    /// A string by key.
    public func t(_ key: String, _ args: CVarArg...) -> String { t(key, args: args) }

    public func t(_ key: String, args: [CVarArg]) -> String {
        switch lookup(key) {
        case let .text(text): return Self.format(text, args)
        case let .plural(forms): return Self.format(forms["other"] ?? "", args)
        case nil: return key
        }
    }

    /// A plural by key and count, with the count as the first argument.
    public func n(_ key: String, _ count: Int, _ args: CVarArg...) -> String {
        let all: [CVarArg] = [count] + args
        switch lookup(key) {
        case let .text(text): return Self.format(text, all)
        case let .plural(forms): return Self.format(forms[Self.pluralForm(language, count)] ?? forms["other"] ?? "", all)
        case nil: return key
        }
    }

    /// CLDR cardinal rules for the two languages the app speaks, for whole numbers.
    static func pluralForm(_ language: Language, _ n: Int) -> String {
        let n = abs(n)
        switch language {
        case .en:
            return n == 1 ? "one" : "other"
        case .ru:
            let mod10 = n % 10
            let mod100 = n % 100
            if mod10 == 1 && mod100 != 11 { return "one" }
            if (2...4).contains(mod10) && !(12...14).contains(mod100) { return "few" }
            return "many"
        }
    }

    /// `%1$s`, `%2$d`, `%s`, `%d` and `%%`, in the way Android's `getString` reads them.
    public static func format(_ template: String, _ args: [CVarArg]) -> String {
        guard template.contains("%") else { return template }
        var out = ""
        var next = 0
        let chars = Array(template)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            guard c == "%" else {
                out.append(c)
                i += 1
                continue
            }
            // %[n$](s|d|%)
            var j = i + 1
            var digits = ""
            while j < chars.count, chars[j].isASCII, chars[j].isNumber {
                digits.append(chars[j])
                j += 1
            }
            var position: Int?
            if !digits.isEmpty, j < chars.count, chars[j] == "$" {
                position = Int(digits)
                j += 1
            } else {
                j = i + 1
            }
            guard j < chars.count, "sd%".contains(chars[j]) else {
                out.append(c)
                i += 1
                continue
            }
            let type = chars[j]
            if type == "%" {
                out.append("%")
            } else {
                let index = position.map { $0 - 1 } ?? { defer { next += 1 }; return next }()
                if index >= 0, index < args.count { out += Self.describe(args[index]) }
            }
            i = j + 1
        }
        return out
    }

    private static func describe(_ value: CVarArg) -> String {
        switch value {
        case let v as String: return v
        case let v as Int: return String(v)
        case let v as Int64: return String(v)
        case let v as Double: return String(v)
        default: return "\(value)"
        }
    }
}

/// The extension keys whose meaning survives on a Mac. Everything else in `ext.*.json` is about a browser.
enum ExtensionWords {
    static let keys: Set<String> = [
        "x_export_confirm", "x_link_copied", "x_loading",
        "x_split_add", "x_split_add_current", "x_split_add_placeholder", "x_split_empty", "x_split_full", "x_split_invalid",
        "x_split_remove", "x_split_selected", "x_split_status_exclude", "x_split_status_include", "x_split_status_off",
        "x_split_mode_exclude", "x_split_mode_exclude_body", "x_split_mode_include", "x_split_mode_include_body",
        "x_split_mode_off",
        "x_site_bypass", "x_site_direct", "x_site_through", "x_site_use",
        "x_engine_install", "x_engine_installed", "x_engine_installing", "x_engine_failed",
        "x_guide_how",
    ]
}
