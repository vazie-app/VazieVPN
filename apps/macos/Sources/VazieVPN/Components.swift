import AppKit
import SwiftUI
import VazieCore

// MARK: navigation

enum Route: Hashable {
    case add, details, settings, appearance, split, mode, engine, guides, faq, about, privacy
    case guide(String)
    case config(String)
}

@MainActor
final class Navigator: ObservableObject {
    @Published var stack: [Route] = []
    @Published var toast: String?
    private var toastTask: Task<Void, Never>?

    func push(_ route: Route) { stack.append(route) }
    func pop() { if !stack.isEmpty { stack.removeLast() } }
    func home() { stack.removeAll() }
    func replaceAll(_ route: Route) { stack = [route] }

    func show(_ message: String) {
        toast = message
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if !Task.isCancelled { toast = nil }
        }
    }
}

// MARK: icons

/// The app's icons, drawn with the system's symbols.
enum VIcon: String {
    case gear = "gearshape", back = "chevron.left", chevron = "chevron.right", plus, power, trash, edit = "pencil", copy = "doc.on.doc"
    case share = "square.and.arrow.up", check = "checkmark", close = "xmark", paste = "doc.on.clipboard", paintpalette, split = "arrow.triangle.branch"
    case book, help = "questionmark.circle", bug = "ladybug", mail = "envelope", info = "info.circle", lock, refresh = "arrow.clockwise"
    case engine = "cpu", bell, menubar = "menubar.rectangle", dock = "dock.rectangle", globe, network, eye, eyeSlash = "eye.slash"
}

struct VSymbol: View {
    let icon: VIcon
    var size: CGFloat = 17
    var weight: Font.Weight = .medium

    var body: some View {
        Image(systemName: icon.rawValue).font(.system(size: size, weight: weight)).accessibilityHidden(true)
    }
}

// MARK: frame

struct VScreen<Content: View, Trailing: View>: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var nav: Navigator
    let title: String
    var showsBack = true
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if showsBack {
                    VIconButton(icon: .back, label: "←") { (onBack ?? nav.pop)() }
                        .keyboardShortcut(.cancelAction)
                }
                Text(title).vText(18, 650).lineLimit(1)
                Spacer(minLength: 0)
                trailing()
            }
            .padding(.horizontal, Radius.gutter).padding(.top, 14).padding(.bottom, 10).frame(minHeight: 56)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) { content() }
                    .padding(.horizontal, Radius.gutter).padding(.bottom, 24)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.automatic)
        }
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
        .background(theme.background)
    }
}

extension VScreen where Trailing == EmptyView {
    init(title: String, showsBack: Bool = true, onBack: (() -> Void)? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.init(title: title, showsBack: showsBack, onBack: onBack, trailing: { EmptyView() }, content: content)
    }
}

// MARK: controls

struct VIconButton: View {
    @Environment(\.theme) var theme
    let icon: VIcon
    let label: String
    var tonal = false
    var size: CGFloat = 40
    let action: () -> Void
    @State private var hover = false

    var body: some View {
        Button(action: action) {
            VSymbol(icon: icon, size: size > 38 ? 17 : 14)
                .foregroundStyle(tonal ? theme.onAccentContainer : theme.text)
                .frame(width: size, height: size)
                .background(RoundedRectangle(cornerRadius: 14).fill(tonal ? theme.accentContainer : hover ? theme.surfaceMuted : .clear))
        }
        .buttonStyle(.plain)
        .onHover { hover = $0 }
        .accessibilityLabel(label)
        .help(label)
    }
}

enum VButtonVariant { case primary, secondary, destructive, outline, text }

struct VButton: View {
    @Environment(\.theme) var theme
    @Environment(\.isEnabled) var enabled
    let title: String
    var variant: VButtonVariant = .primary
    var icon: VIcon?
    var compact = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let icon { VSymbol(icon: icon, size: 15) }
                Text(title).vText(compact ? 13 : 15, 600, foregroundKey).lineLimit(1)
            }
            .foregroundStyle(theme[keyPath: foregroundKey])
            .frame(maxWidth: compact ? nil : .infinity)
            .padding(.horizontal, compact ? 14 : 18)
            .frame(minHeight: compact ? 34 : 52)
            .background(RoundedRectangle(cornerRadius: compact ? 12 : Radius.md).fill(background))
            .overlay(RoundedRectangle(cornerRadius: compact ? 12 : Radius.md).stroke(variant == .outline ? theme.border : .clear, lineWidth: 1.5))
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }

    private var foregroundKey: KeyPath<Theme, Color> {
        switch variant {
        case .primary: return \.onAction
        case .secondary: return \.onAccentContainer
        case .destructive: return \.onErrorContainer
        case .outline, .text: return \.primary
        }
    }

    private var background: Color {
        switch variant {
        case .primary: return theme.action
        case .secondary: return theme.accentContainer
        case .destructive: return theme.errorContainer
        case .outline, .text: return .clear
        }
    }
}

struct VCard<Content: View>: View {
    @Environment(\.theme) var theme
    var padded = false
    var divided = false
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(padded ? 16 : 0)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(theme.surface))
            .overlay(RoundedRectangle(cornerRadius: Radius.lg).stroke(theme.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }
}

struct VSection: View {
    @Environment(\.theme) var theme
    let title: String
    var trailing: (String, () -> Void)?

    var body: some View {
        HStack {
            Text(title).vText(13, 600, \.muted).textCase(nil)
            Spacer()
            if let trailing {
                Button(trailing.0, action: trailing.1).buttonStyle(.plain).vText(13, 600, \.primary)
            }
        }
        .padding(.horizontal, 4).padding(.top, 22).padding(.bottom, 8)
        .accessibilityAddTraits(.isHeader)
    }
}

/// A row of a list: an icon, a title and a line under it, and whatever trails.
struct VRow<Trailing: View>: View {
    @Environment(\.theme) var theme
    var icon: VIcon?
    let title: String
    var subtitle: String?
    var destructive = false
    var chevron = false
    var selected = false
    var action: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        Button(action: { action?() }) {
            HStack(spacing: 12) {
                if let icon { VSymbol(icon: icon, size: 18).foregroundStyle(destructive ? theme.error : theme.primary).frame(width: 24) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).vText(15, 550, destructive ? \.error : \.text).multilineTextAlignment(.leading)
                    if let subtitle { Text(subtitle).vText(12.5, 400, \.text2).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true) }
                }
                Spacer(minLength: 8)
                trailing()
                if selected { VSymbol(icon: .check, size: 15).foregroundStyle(theme.primary) }
                if chevron { VSymbol(icon: .chevron, size: 13).foregroundStyle(theme.muted) }
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowStyle())
        .disabled(action == nil)
    }

    private struct RowStyle: ButtonStyle {
        @Environment(\.theme) var theme
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.background(configuration.isPressed ? theme.surfaceMuted : .clear)
        }
    }
}

extension VRow where Trailing == EmptyView {
    init(
        icon: VIcon? = nil, title: String, subtitle: String? = nil, destructive: Bool = false, chevron: Bool = false, selected: Bool = false,
        action: (() -> Void)? = nil
    ) {
        self.init(icon: icon, title: title, subtitle: subtitle, destructive: destructive, chevron: chevron, selected: selected, action: action, trailing: { EmptyView() })
    }
}

struct VDivider: View {
    @Environment(\.theme) var theme
    var body: some View { Rectangle().fill(theme.divider).frame(height: 1).padding(.leading, 16) }
}

/// Rows of a card, with a hairline between them.
struct VList<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        VCard {
            _VariadicView.Tree(Separated()) { content() }
        }
    }

    private struct Separated: _VariadicView_UnaryViewRoot {
        func body(children: _VariadicView.Children) -> some View {
            VStack(spacing: 0) {
                ForEach(Array(children.enumerated()), id: \.offset) { index, child in
                    if index > 0 { VDivider() }
                    child
                }
            }
        }
    }
}

struct VDetailRow: View {
    @Environment(\.theme) var theme
    let label: String
    let value: String
    var mono = true
    var trailing: AnyView?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(label).vText(13, 400, \.text2)
            Spacer(minLength: 12)
            Text(value).font(mono ? Fonts.mono(12.5) : Fonts.sans(14, 500)).foregroundStyle(theme.text)
                .multilineTextAlignment(.trailing).textSelection(.enabled)
            if let trailing { trailing }
        }
        .padding(.horizontal, 16).padding(.vertical, 11)
        .overlay(alignment: .bottom) { VDivider() }
    }
}

enum VTone { case info, success, warning, error }

struct VBanner: View {
    @Environment(\.theme) var theme
    var tone: VTone = .info
    var title: String?
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let title { Text(title).vText(14, 650, on) }
            Text(message).vText(13, 400, on).fixedSize(horizontal: false, vertical: true)
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(theme[keyPath: container]))
        .padding(.top, 10)
        .accessibilityElement(children: .combine)
    }

    private var container: KeyPath<Theme, Color> {
        switch tone {
        case .info: return \.accentContainer
        case .success: return \.successContainer
        case .warning: return \.warningContainer
        case .error: return \.errorContainer
        }
    }

    private var on: KeyPath<Theme, Color> {
        switch tone {
        case .info: return \.onAccentContainer
        case .success: return \.onSuccessContainer
        case .warning: return \.onWarningContainer
        case .error: return \.onErrorContainer
        }
    }
}

struct VNote: View {
    let text: String
    var body: some View { Text(text).vText(12.5, 400, \.muted).fixedSize(horizontal: false, vertical: true).padding(.horizontal, 4).padding(.top, 8) }
}

struct VBody: View {
    let text: String
    var body: some View { Text(text).vText(14.5, 400, \.text2).lineSpacing(3).fixedSize(horizontal: false, vertical: true).padding(.horizontal, 4).padding(.vertical, 4) }
}

struct VHeading: View {
    let text: String
    var body: some View { Text(text).vText(16, 650).padding(.horizontal, 4).padding(.top, 18).padding(.bottom, 4).accessibilityAddTraits(.isHeader) }
}

struct VTextField: View {
    @Environment(\.theme) var theme
    let label: String
    @Binding var text: String
    var placeholder = ""
    var mono = false
    var multiline = false
    var error: String?
    var onSubmit: () -> Void = {}
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).vText(12.5, 600, \.text2)
            Group {
                if multiline {
                    TextField(placeholder, text: $text, axis: .vertical).lineLimit(3...6)
                } else {
                    TextField(placeholder, text: $text).onSubmit(onSubmit)
                }
            }
            .textFieldStyle(.plain)
            .font(mono ? Fonts.mono(13) : Fonts.sans(15))
            .foregroundStyle(theme.text)
            .focused($focused)
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: Radius.sm).fill(theme.surface))
            .overlay(RoundedRectangle(cornerRadius: Radius.sm).stroke(error != nil ? theme.error : focused ? theme.primary : theme.border, lineWidth: focused ? 2 : 1))
            if let error { Text(error).vText(12.5, 500, \.error) }
        }
    }
}

/// A modal question: dimmed content behind, one answer in front (`VazieDialogs.kt`).
struct VDialog<Content: View>: View {
    @Environment(\.theme) var theme
    let title: String
    var message: String?
    let confirm: String
    var cancel: String?
    var destructive = false
    var confirmDisabled = false
    let onConfirm: () -> Void
    var onCancel: () -> Void = {}
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            theme.scrim.ignoresSafeArea().onTapGesture { onCancel() }
            VStack(alignment: .leading, spacing: 12) {
                Text(title).vText(19, 650)
                if let message { Text(message).vText(14, 400, \.text2).fixedSize(horizontal: false, vertical: true) }
                content()
                HStack(spacing: 8) {
                    Spacer()
                    if let cancel { VButton(title: cancel, variant: .text, compact: true, action: onCancel).keyboardShortcut(.cancelAction) }
                    VButton(title: confirm, variant: destructive ? .destructive : .primary, compact: true, action: onConfirm)
                        .disabled(confirmDisabled).keyboardShortcut(.defaultAction)
                }
                .padding(.top, 6)
            }
            .padding(22).frame(maxWidth: 400)
            .background(RoundedRectangle(cornerRadius: Radius.xl).fill(theme.elevated))
            .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(theme.border, lineWidth: 1))
            .padding(24)
        }
        .transition(.opacity)
        .accessibilityAddTraits(.isModal)
    }
}

extension VDialog where Content == EmptyView {
    init(title: String, message: String? = nil, confirm: String, cancel: String? = nil, destructive: Bool = false, onConfirm: @escaping () -> Void, onCancel: @escaping () -> Void = {}) {
        self.init(title: title, message: message, confirm: confirm, cancel: cancel, destructive: destructive, onConfirm: onConfirm, onCancel: onCancel, content: { EmptyView() })
    }
}

/// The "VL" chip beside a configuration.
struct VMark: View {
    @Environment(\.theme) var theme
    let text: String

    var body: some View {
        Text(text).font(Fonts.mono(12, 600)).foregroundStyle(theme.onAccentContainer)
            .frame(width: 40, height: 40)
            .background(RoundedRectangle(cornerRadius: 12).fill(theme.accentContainer))
            .accessibilityHidden(true)
    }
}

struct VChip: View {
    @Environment(\.theme) var theme
    let label: String
    var detail: String?
    var tone: VTone = .info

    var body: some View {
        HStack(spacing: 8) {
            Text(label).vText(13, 600, on).padding(.horizontal, 12).padding(.vertical, 5)
                .background(Capsule().fill(theme[keyPath: container]))
            if let detail { Text(detail).font(Fonts.mono(12)).foregroundStyle(theme.text2) }
        }
    }

    private var container: KeyPath<Theme, Color> {
        switch tone {
        case .info: return \.accentContainer
        case .success: return \.successContainer
        case .warning: return \.warningContainer
        case .error: return \.errorContainer
        }
    }

    private var on: KeyPath<Theme, Color> {
        switch tone {
        case .info: return \.onAccentContainer
        case .success: return \.onSuccessContainer
        case .warning: return \.onWarningContainer
        case .error: return \.onErrorContainer
        }
    }
}

struct VToast: View {
    @Environment(\.theme) var theme
    let message: String

    var body: some View {
        Text(message).vText(13.5, 500, \.text)
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(Capsule().fill(theme.elevated))
            .overlay(Capsule().stroke(theme.border, lineWidth: 1))
            .shadow(color: .black.opacity(0.25), radius: 12, y: 4)
            .padding(.bottom, 22)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityAddTraits(.updatesFrequently)
    }
}

/// A sample link, set apart in mono (`VazieExampleBlock.kt`).
struct VExample: View {
    @Environment(\.theme) var theme
    let text: String

    var body: some View {
        Text(text).font(Fonts.mono(12)).foregroundStyle(theme.text2).textSelection(.enabled)
            .padding(14).frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.sm).fill(theme.surfaceMuted))
    }
}

/// A one-sentence hint about a gesture or a place, shown until it is dismissed (the app's coach marks).
struct VTip: View {
    @EnvironmentObject var model: AppModel
    let id: String
    let title: String
    let message: String

    var body: some View {
        let t = model.t
        if model.prefs.tourDone, !model.prefs.dismissedTips.contains(id) {
            VCard {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(t.t(title)).vText(14.5, 600)
                        Text(t.t(message)).vText(12.5, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer()
                    VIconButton(icon: .close, label: t.t("config_close"), size: 30) { model.setPrefs { $0.dismissedTips.append(id) } }
                }
                .padding(.horizontal, 16).padding(.vertical, 12)
            }
            .padding(.top, 8)
        }
    }
}
