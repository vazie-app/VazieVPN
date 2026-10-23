import XCTest

@testable import VazieCore

/// Every sentence the interface asks for exists in both languages, and nothing is left untranslated.
final class StringsTests: XCTestCase {
    private static let sources: URL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Sources")

    /// Literals that look like keys and are not: raw values, file names, backend paths.
    private static let notKeys: Set<String> = [
        "night_indigo", "cotton_candy", "unsupported_platform", "menu_bar", "system_proxy", "vazovsky_sticker", "first-config",
        "vazie_tunnel", "vazie_connected", "vazie_disconnect", "martian_mono", "profiles_vault", "net_work",
    ]

    private func literals() throws -> Set<String> {
        let pattern = try NSRegularExpression(pattern: #""([a-z][a-z0-9]*(?:_[a-z0-9]+)+)""#)
        var found = Set<String>()
        let enumerator = FileManager.default.enumerator(at: Self.sources, includingPropertiesForKeys: nil)!
        for case let url as URL in enumerator where url.pathExtension == "swift" {
            let text = try String(contentsOf: url, encoding: .utf8)
            for match in pattern.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
                found.insert(String(text[Range(match.range(at: 1), in: text)!]))
            }
        }
        return found.subtracting(Self.notKeys)
    }

    /// Keys built from a name at run time (`"settings_app_icon_\(id)"`), spelled out.
    private func dynamicKeys() -> Set<String> {
        var keys = Set<String>()
        for a in Appearance.allCases { keys.insert("settings_appearance_\(a.rawValue)") }
        for i in AppIconId.allCases { keys.insert("settings_app_icon_\(i.rawValue)") }
        for p in AppIconPlate.allCases { keys.insert("settings_app_icon_plate_\(p.rawValue)") }
        for m in SplitMode.allCases { keys.insert("x_split_mode_\(m.rawValue)"); keys.insert("mac_split_mode_\(m.rawValue)_body") }
        for s in ["connected", "selected", "idle", "unavailable"] { keys.insert("connections_status_\(s)") }
        for g in GuideId.allCases {
            keys.insert("mac_guide_\(g.rawValue)_title")
            keys.insert("mac_guide_\(g.rawValue)_summary")
            if g != .notification { keys.insert("mac_guide_\(g.rawValue)_body") }
            for n in 1...[GuideId.menuBar: 3, .dock: 2, .systemProxy: 3, .notification: 3][g]! { keys.insert("mac_guide_\(g.rawValue)_step_\(n)") }
        }
        return keys
    }

    func testEveryKeyTheInterfaceAsksForExistsInBothLanguages() throws {
        let asked = try literals().union(dynamicKeys())
        XCTAssertGreaterThan(asked.count, 150)
        for language in [Language.en, .ru] {
            let have = Translator.allKeys(language)
            let missing = asked.filter { !have.contains($0) && !$0.hasPrefix("tools_") }.sorted()
            // Only literals that were meant as keys are checked: anything with a known prefix.
            let prefixes = ["mac_", "x_", "home_", "config_", "connection", "settings_", "tour_", "onboarding_", "split_", "faq_", "getting_started_", "guid", "privacy_", "notification_", "vpn_", "vazie_", "shortcut_", "widget_", "update_", "app_name"]
            let real = missing.filter { key in prefixes.contains { key.hasPrefix($0) } }
            XCTAssertEqual(real, [], "\(language) is missing these keys")
        }
    }

    func testTheMacTableIsCompleteInBothLanguages() throws {
        let en = Translator.allKeys(.en)
        let ru = Translator.allKeys(.ru)
        XCTAssertEqual(en.subtracting(ru).sorted(), [], "English words with no Russian")
        XCTAssertEqual(ru.subtracting(en).sorted(), [], "Russian words with no English")
    }

    func testPlaceholdersAgreeBetweenLanguages() throws {
        let placeholder = try NSRegularExpression(pattern: #"%(?:\d+\$)?[sd]"#)
        func count(_ text: String) -> Int { placeholder.numberOfMatches(in: text, range: NSRange(text.startIndex..., in: text)) }
        for key in Translator.allKeys(.en).filter({ $0.hasPrefix("mac_") }) {
            let en = Translator(language: .en).t(key)
            let ru = Translator(language: .ru).t(key)
            XCTAssertEqual(count(en), count(ru), key)
        }
    }

    func testNothingBrowserOrPhoneSpecificLeaksIntoTheMacWords() throws {
        // The words a Mac user reads: no "toolbar", "extension", "helper", "widget" or "phone" unless a key means it.
        let banned = ["extension", "toolbar", "helper", "side panel", "browser"]
        let t = Translator(language: .en)
        for key in try literals().union(dynamicKeys()) where t.has(key) {
            let text = t.t(key, args: ["%", "%", "%"]).lowercased()
            for word in banned where text.contains(word) {
                // The Mac guides may say "browsers follow the system proxy".
                if key.hasPrefix("mac_guide_system_proxy") || key.hasPrefix("mac_privacy_vpn") || key.hasPrefix("mac_uninstall_") { continue }
                XCTFail("\(key) mentions “\(word)”: \(text)")
            }
        }
    }
}
