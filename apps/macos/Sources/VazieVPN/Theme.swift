import AppKit
import CoreText
import SwiftUI
import VazieCore

// "Маршрут": the app's two palettes (VazieColors.kt), its type (Geologica for text, Martian Mono for technical
// values), its radii (VazieShapes.kt) and its 4-px spacing — the same values the extension's theme.css carries.

extension Color {
    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&value)
        self.init(
            .sRGB, red: Double((value >> 16) & 0xFF) / 255, green: Double((value >> 8) & 0xFF) / 255, blue: Double(value & 0xFF) / 255, opacity: 1)
    }
}

struct Theme {
    var isDark: Bool
    var background, surface, elevated, primary, onPrimary, primaryPressed, accent, text, text2, muted, disabledText: Color
    var success, warning, error, border, divider, surfaceMuted, fieldDisabled: Color
    var accentContainer, onAccentContainer, errorContainer, onErrorContainer, warningContainer, onWarningContainer: Color
    var successContainer, onSuccessContainer, routeStart, routeEnd, routeSoft, action, onAction, actionPressed, scrim: Color

    static let nightIndigo = Theme(
        isDark: true, background: Color(hex: "0d1020"), surface: Color(hex: "151932"), elevated: Color(hex: "1c2142"),
        primary: Color(hex: "9db0ff"), onPrimary: Color(hex: "0d1020"), primaryPressed: Color(hex: "8497eb"), accent: Color(hex: "c9b6f2"),
        text: Color(hex: "eef0fa"), text2: Color(hex: "aab0cf"), muted: Color(hex: "8a91b4"), disabledText: Color(hex: "5b6186"),
        success: Color(hex: "9fd8b8"), warning: Color(hex: "e8c47c"), error: Color(hex: "f0a77f"), border: Color(hex: "323969"),
        divider: Color(hex: "262c54"), surfaceMuted: Color(hex: "262c54"), fieldDisabled: Color(hex: "171a2e"),
        accentContainer: Color(hex: "2b3163"), onAccentContainer: Color(hex: "dce2ff"), errorContainer: Color(hex: "3a2620"),
        onErrorContainer: Color(hex: "f7cdb5"), warningContainer: Color(hex: "33291a"), onWarningContainer: Color(hex: "f1ddb3"),
        successContainer: Color(hex: "1e3228"), onSuccessContainer: Color(hex: "e6f2ea"), routeStart: Color(hex: "9db0ff"),
        routeEnd: Color(hex: "c9b6f2"), routeSoft: Color(hex: "2b3163"), action: Color(hex: "f1e3c8"), onAction: Color(hex: "241c14"),
        actionPressed: Color(hex: "e2d2b3"), scrim: Color(red: 5 / 255, green: 6 / 255, blue: 13 / 255, opacity: 0.62))

    static let milk = Theme(
        isDark: false, background: Color(hex: "faf8f4"), surface: Color(hex: "fffefc"), elevated: Color(hex: "ffffff"),
        primary: Color(hex: "4a5bc4"), onPrimary: Color(hex: "fffefc"), primaryPressed: Color(hex: "3d4dae"), accent: Color(hex: "5d43a8"),
        text: Color(hex: "171a2e"), text2: Color(hex: "4e5470"), muted: Color(hex: "646a86"), disabledText: Color(hex: "8e93ab"),
        success: Color(hex: "2f7a55"), warning: Color(hex: "9a6b12"), error: Color(hex: "b4532a"), border: Color(hex: "e6e7f0"),
        divider: Color(hex: "ecebf2"), surfaceMuted: Color(hex: "f0eff4"), fieldDisabled: Color(hex: "f3f2f6"),
        accentContainer: Color(hex: "e4e7f8"), onAccentContainer: Color(hex: "1f2a6b"), errorContainer: Color(hex: "f8e4d8"),
        onErrorContainer: Color(hex: "6a2f14"), warningContainer: Color(hex: "f6ebcf"), onWarningContainer: Color(hex: "4f3a0b"),
        successContainer: Color(hex: "ddefe4"), onSuccessContainer: Color(hex: "173d2a"), routeStart: Color(hex: "4a5bc4"),
        routeEnd: Color(hex: "5d43a8"), routeSoft: Color(hex: "e4e7f8"), action: Color(hex: "1d1f3a"), onAction: Color(hex: "f6eedd"),
        actionPressed: Color(hex: "2c2f52"), scrim: Color(red: 23 / 255, green: 26 / 255, blue: 46 / 255, opacity: 0.5))

    static func of(_ appearance: Appearance) -> Theme { appearance == .milk ? .milk : .nightIndigo }
}

private struct ThemeKey: EnvironmentKey { static let defaultValue = Theme.nightIndigo }

extension EnvironmentValues {
    var theme: Theme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}

enum Radius {
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 22
    static let xl: CGFloat = 28
    static let gutter: CGFloat = 20
}

enum Fonts {
    private static var registered = false

    /// Registers the two typefaces for this process; the first call does the work.
    static func register() {
        guard !registered else { return }
        registered = true
        for name in ["geologica", "martian_mono"] {
            if let url = Bundle.module.url(forResource: name, withExtension: "ttf", subdirectory: "fonts") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }

    private static let weightAxis = 2_003_265_652 as NSNumber

    private static func variable(_ family: String, _ size: CGFloat, weight: CGFloat) -> Font {
        let base = CTFontCreateWithName(family as CFString, size, nil)
        let attributes: [CFString: Any] = [kCTFontVariationAttribute: [weightAxis: weight]]
        let descriptor = CTFontDescriptorCreateWithAttributes(attributes as CFDictionary)
        return Font(CTFontCreateCopyWithAttributes(base, size, nil, descriptor))
    }

    /// Geologica, at a weight on its 100…900 axis.
    static func sans(_ size: CGFloat, _ weight: CGFloat = 400) -> Font { variable("Geologica", size, weight: weight) }

    /// Martian Mono, for technical values.
    static func mono(_ size: CGFloat, _ weight: CGFloat = 400) -> Font { variable("Martian Mono", size, weight: min(600, max(400, weight))) }
}

extension View {
    func vText(_ size: CGFloat = 14, _ weight: CGFloat = 400, _ color: KeyPath<Theme, Color> = \.text) -> some View {
        modifier(VText(size: size, weight: weight, color: color))
    }
}

private struct VText: ViewModifier {
    @Environment(\.theme) var theme
    let size: CGFloat
    let weight: CGFloat
    let color: KeyPath<Theme, Color>

    func body(content: Content) -> some View {
        content.font(Fonts.sans(size, weight)).foregroundStyle(theme[keyPath: color])
    }
}
