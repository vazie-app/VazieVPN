import SwiftUI
import VazieCore

struct TourTargets: PreferenceKey {
    static var defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) { value.merge(nextValue()) { $1 } }
}

extension View {
    func tourTarget(_ id: String) -> some View { anchorPreference(key: TourTargets.self, value: .bounds) { [id: $0] } }
}

struct HomeScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    @State private var deleting: ProfileSummary?
    @State private var askNotifications = false
    @State private var now = Date()
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        let t = model.t
        let view = HomeView.make(snapshot: model.snapshot, profiles: model.profiles, selectedId: model.selectedId)
        let presentation = present(view)
        ZStack {
            VStack(spacing: 0) {
                HStack {
                    (Text("Vazie ").foregroundStyle(theme.text) + Text("VPN").foregroundStyle(theme.primary)).font(Fonts.sans(17, 650))
                    Spacer()
                    VIconButton(icon: .gear, label: t.t("home_a11y_settings")) { nav.push(.settings) }.tourTarget("settings")
                }
                .padding(.horizontal, Radius.gutter).padding(.top, 14).padding(.bottom, 10).frame(minHeight: 56)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Headline(presentation: presentation, now: now)
                        if let route = presentation.route { RouteView(presentation: presentation, state: route, view: view) }
                        banners(t)
                        VButton(title: t.t(presentation.actionKey), variant: variant(presentation.actionVariant), icon: presentation.action == .add ? .plus : .power) {
                            Task { await act(presentation.action) }
                        }
                        .disabled(!presentation.actionEnabled).padding(.top, 14).tourTarget("connect")
                        .keyboardShortcut(.return, modifiers: .command)
                        if case .lost = model.snapshot.state {
                            // Reconnect is the main button; this is how out, and, with the Kill Switch holding the traffic,
                            // how to get the internet back.
                            VButton(title: t.t("home_action_disconnect"), variant: .secondary) { Task { await model.disconnect() } }.padding(.top, 8)
                        }
                        Configurations(deleting: $deleting).tourTarget("configs")
                    }
                    .padding(.horizontal, Radius.gutter).padding(.bottom, 24)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .frame(maxWidth: 560).frame(maxWidth: .infinity)

            if let item = deleting {
                VDialog(
                    title: t.t("connections_delete_title"), message: t.t("connections_delete_body", item.name),
                    confirm: t.t("connections_delete_confirm"), cancel: t.t("connections_delete_cancel"), destructive: true,
                    onConfirm: {
                        deleting = nil
                        Task { await model.remove(item.id) }
                    }, onCancel: { deleting = nil })
            }
            if askNotifications {
                VDialog(
                    title: t.t("notification_prompt_title"), message: t.t("mac_notification_prompt_body"),
                    confirm: t.t("notification_prompt_allow"), cancel: t.t("notification_prompt_not_now"),
                    onConfirm: {
                        askNotifications = false
                        Task {
                            let granted = await Notifier.shared.requestAuthorization()
                            model.setPrefs { $0.notificationsEnabled = granted; $0.notificationPromptShown = true }
                            await model.connect()
                        }
                    },
                    onCancel: {
                        askNotifications = false
                        model.setPrefs { $0.notificationPromptShown = true }
                        Task { await model.connect() }
                    })
            }
        }
        .overlayPreferenceValue(TourTargets.self) { targets in TourOverlay(targets: targets) }
        .onReceive(tick) { now = $0 }
        .task { await model.refreshOtherVPNs() }
        .onAppear { model.beginWatching() }
        .onDisappear { model.endWatching() }
    }

    private func variant(_ v: ActionVariant) -> VButtonVariant {
        switch v {
        case .primary: return .primary
        case .secondary: return .secondary
        case .destructive: return .destructive
        }
    }

    /// What a previous run left on this Mac, with the one button that puts it right.
    @ViewBuilder private func recoveryBanner(_ t: Translator) -> some View {
        if let leftover = model.leftover {
            let body: String = {
                switch leftover {
                case .settings: return t.t("mac_recovery_banner_settings")
                case let .supervisor(blocking): return t.t(blocking ? "mac_recovery_banner_blocking" : "mac_recovery_banner_supervisor")
                }
            }()
            VStack(alignment: .leading, spacing: 8) {
                VBanner(tone: .warning, title: t.t("mac_recovery_banner_title"), message: body)
                VButton(title: t.t("mac_recovery_action"), variant: .secondary, compact: true) {
                    Task { if !(await model.restoreNetwork()) { nav.show(t.t("mac_recovery_failed")) } }
                }
            }
            .padding(.bottom, 8)
        }
    }

    @ViewBuilder private func banners(_ t: Translator) -> some View {
        recoveryBanner(t)
        if !model.otherVPNs.isEmpty, !model.snapshot.state.isConnected {
            VBanner(tone: .warning, message: t.t("mac_other_vpn_banner", model.otherVPNs.joined(separator: ", ")))
        }
        if case .failed(.engine) = model.snapshot.state {
            engineBanner(t)
        } else if case .missing = model.engine, !model.profiles.isEmpty {
            engineBanner(t)
        }
    }

    private func engineBanner(_ t: Translator) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            VBanner(tone: .warning, title: t.t("mac_engine_needed_title"), message: t.t("mac_engine_needed_body"))
            VButton(title: t.t("mac_engine_set_up"), variant: .secondary, compact: true) { nav.push(.engine) }
        }
    }

    private func act(_ action: HomeAction) async {
        switch action {
        case .add: nav.push(.add)
        case .connect:
            if !model.prefs.notificationPromptShown, await Notifier.shared.status() == .notDetermined {
                askNotifications = true
            } else {
                await model.connect()
            }
        case .retry, .check: await model.connect()
        case .cancel, .disconnect: await model.disconnect()
        }
    }
}

private struct Headline: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    let presentation: Presentation
    let now: Date

    var body: some View {
        let t = model.t
        let seconds: Int = {
            if case let .connected(_, since) = model.snapshot.state { return max(0, Int(now.timeIntervalSince(since))) }
            return 0
        }()
        let name = model.snapshot.subject?.name ?? model.profiles.first { $0.id == model.selectedId }?.name ?? ""
        HStack(spacing: 14) {
            VSymbol(icon: .power, size: 22)
                .foregroundStyle(tileForeground)
                .frame(width: 52, height: 52)
                .background(RoundedRectangle(cornerRadius: 16).fill(tileBackground))
            VStack(alignment: .leading, spacing: 2) {
                Text(presentation.titleKey.map { t.t($0) } ?? "").vText(22, 650)
                switch presentation.line {
                case let .session(protocolLabel):
                    Text(t.t("home_route_session", Format.sessionDuration(seconds: seconds), protocolLabel)).font(Fonts.mono(13)).foregroundStyle(theme.text2)
                case let .words(key):
                    Text(words(key, t)).vText(14, 400, \.text2)
                case let .wordsNamed(key):
                    Text(t.t(key, name)).vText(14, 400, \.text2)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }

    /// The sentence for a state; when another VPN holds the proxy, it is named.
    private func words(_ key: String, _ t: Translator) -> String {
        if key == "mac_failure_proxy_taken", !model.conflictingVPNs.isEmpty {
            return t.t("mac_failure_proxy_taken_named", model.conflictingVPNs.joined(separator: ", "))
        }
        return t.t(key)
    }

    private var tileForeground: Color {
        switch presentation.tone {
        case .success: return theme.onSuccessContainer
        case .error: return theme.onErrorContainer
        default: return theme.onAccentContainer
        }
    }

    private var tileBackground: Color {
        switch presentation.tone {
        case .success: return theme.successContainer
        case .error: return theme.errorContainer
        default: return theme.accentContainer
        }
    }
}

/// This Mac, the route, and the server: the picture at the middle of Home (`VazieRoute.kt`).
private struct RouteView: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    let presentation: Presentation
    let state: RouteState
    let view: HomeView
    @State private var phase: CGFloat = 0

    var body: some View {
        let t = model.t
        let live = state == .connected
        let name = model.snapshot.subject?.name ?? model.profiles.first { $0.id == model.selectedId }?.name ?? ""
        let mark = model.snapshot.subject?.mark ?? Protocol_.mark
        VStack(spacing: 8) {
            // One drawing, in the route's own 320 x 64 space: the circles and what sits in them share a centre.
            Canvas { context, size in
                let scale = size.width / 320
                var path = Path()
                path.move(to: CGPoint(x: 46, y: 32))
                path.addCurve(to: CGPoint(x: 274, y: 32), control1: CGPoint(x: 110, y: 6), control2: CGPoint(x: 210, y: 58))
                let scaled = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                let gradient = Gradient(colors: [theme.routeStart, theme.routeEnd])
                let shading = GraphicsContext.Shading.linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: size.width, y: 0))
                if live {
                    context.stroke(scaled, with: shading, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                } else {
                    let color = state == .failed ? theme.error : theme.border
                    context.stroke(scaled, with: .color(color), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: state == .connecting ? [4, 7] : [2, 8], dashPhase: state == .connecting ? phase : 0))
                }
                for (index, x) in [26.0, 294.0].enumerated() {
                    let c = CGPoint(x: x * scale, y: 32 * scale)
                    let node = Path(ellipseIn: CGRect(x: c.x - 22 * scale, y: c.y - 22 * scale, width: 44 * scale, height: 44 * scale))
                    context.fill(node, with: .color(live ? theme.successContainer : theme.accentContainer))
                    context.stroke(node, with: .color(live ? theme.success : theme.border), lineWidth: 1.5)
                    if let symbol = context.resolveSymbol(id: index) { context.draw(symbol, at: c, anchor: .center) }
                }
            } symbols: {
                Image(systemName: "network").font(.system(size: 17, weight: .medium)).foregroundStyle(theme.onAccentContainer).tag(0)
                Text(mark).font(Fonts.mono(13, 600)).foregroundStyle(theme.onAccentContainer).tag(1)
            }
            .aspectRatio(320.0 / 64.0, contentMode: .fit)
            .onAppear { withAnimation(.linear(duration: 1).repeatForever(autoreverses: false)) { phase = -11 } }
            HStack {
                Text(t.t("home_route_device")).vText(12, 500, \.muted)
                Spacer()
                Text(name).vText(12, 500, \.muted).lineLimit(1)
            }
            .padding(.horizontal, 6)
            if live { Session() }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: Radius.xl).fill(theme.surface))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(theme.border, lineWidth: 1))
        .padding(.vertical, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(t.t("home_route_a11y", t.t(presentation.titleKey ?? "home_route_title_disconnected"), name))
    }
}

private struct Session: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    var body: some View {
        let t = model.t
        Button { nav.push(.details) } label: {
            HStack {
                reading("↓", model.traffic?.rx, t.t("home_traffic_downloaded"))
                Spacer()
                reading("↑", model.traffic?.tx, t.t("home_traffic_uploaded"))
                VSymbol(icon: .chevron, size: 12).foregroundStyle(theme.muted)
            }
            .padding(.top, 6).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(t.t("home_traffic_a11y"))
    }

    private func reading(_ arrow: String, _ bytes: Int64?, _ caption: String) -> some View {
        HStack(spacing: 6) {
            Text(arrow).foregroundStyle(theme.primary)
            Text(amount(bytes, model.t)).font(Fonts.mono(13, 600)).foregroundStyle(theme.text)
            Text(caption).vText(11.5, 400, \.muted)
        }
    }
}

func amount(_ bytes: Int64?, _ t: Translator) -> String {
    guard let reading = Format.trafficReading(bytes) else { return t.t("home_value_unknown") }
    let unit = ["B": "home_unit_bytes", "KB": "home_unit_kilobytes", "MB": "home_unit_megabytes", "GB": "home_unit_gigabytes"][reading.unit.rawValue]!
    return t.t("home_value_amount", reading.amount, t.t(unit))
}

private struct Configurations: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    @Binding var deleting: ProfileSummary?
    private let refresh = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        let t = model.t
        VSection(title: t.t("connections_my_configs"), trailing: (t.t("connections_add"), { nav.push(.add) }))
        if model.profiles.isEmpty {
            VCard(padded: true) {
                Text(t.t("connections_empty_title")).vText(15, 650)
                Text(t.t("connections_empty_body")).vText(13, 400, \.text2).padding(.top, 4)
            }
        } else {
            VList {
                ForEach(model.profiles) { profile in
                    ConfigRow(profile: profile, deleting: $deleting)
                }
            }
        }
        VTip(id: "swipe", title: "mac_guidance_delete_title", message: "mac_guidance_delete_body")
            .task(id: model.profiles.map(\.id)) { await model.measureLatencies() }
            .onReceive(refresh) { _ in Task { await model.measureLatencies() } }
    }
}

private struct ConfigRow: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    let profile: ProfileSummary
    @Binding var deleting: ProfileSummary?
    @State private var hover = false

    private enum Status { case connected, selected, idle, unavailable }

    private var status: Status {
        if case let .connected(id, _) = model.snapshot.state, id == profile.id { return .connected }
        if !profile.runnable { return .unavailable }
        return model.selectedId == profile.id ? .selected : .idle
    }

    var body: some View {
        let t = model.t
        let status = status
        HStack(spacing: 0) {
            Button { model.select(profile.id) } label: {
                HStack(spacing: 12) {
                    VMark(text: profile.mark)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(profile.name).vText(15, 600).lineLimit(1)
                        Text(profile.protocolLabel).vText(12.5, 400, \.text2)
                    }
                    Spacer(minLength: 8)
                    if let known = model.latencies[profile.id] {
                        Text(known.map { t.t("connections_latency_ms", $0) } ?? t.t("connections_latency_no_answer"))
                            .font(Fonts.mono(11)).foregroundStyle(theme.muted)
                            .accessibilityLabel(known.map { t.t("connections_latency_a11y", $0) } ?? t.t("connections_latency_no_answer_a11y"))
                    }
                    Circle().fill(dot(status)).frame(width: 8, height: 8)
                    Text(t.t("connections_status_" + key(status))).vText(12, 600, color(status)).frame(minWidth: 62, alignment: .leading)
                }
                .padding(.leading, 16).padding(.vertical, 11).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(status == .selected || status == .connected ? .isSelected : [])

            Button { deleting = profile } label: {
                VSymbol(icon: .trash, size: 14).foregroundStyle(theme.muted).frame(width: 32, height: 36)
            }
            .buttonStyle(.plain).opacity(hover ? 1 : 0).accessibilityLabel(t.t("connections_delete")).help(t.t("connections_delete"))
            Button { nav.push(.config(profile.id)) } label: {
                VSymbol(icon: .chevron, size: 13).foregroundStyle(theme.muted).frame(width: 36, height: 40)
            }
            .buttonStyle(.plain).accessibilityLabel(t.t("connections_details"))
        }
        .onHover { hover = $0 }
        .contextMenu {
            Button(t.t("config_action_rename")) { nav.push(.config(profile.id)) }
            Button(t.t("connections_delete"), role: .destructive) { deleting = profile }
        }
    }

    private func key(_ s: Status) -> String {
        switch s {
        case .connected: return "connected"
        case .selected: return "selected"
        case .idle: return "idle"
        case .unavailable: return "unavailable"
        }
    }

    private func color(_ s: Status) -> KeyPath<Theme, Color> {
        switch s {
        case .connected: return \.success
        case .selected: return \.primary
        case .idle: return \.muted
        case .unavailable: return \.error
        }
    }

    private func dot(_ s: Status) -> Color {
        switch s {
        case .connected: return theme.success
        case .selected: return theme.primary
        case .idle: return theme.border
        case .unavailable: return theme.error
        }
    }
}

// MARK: tour

/// The first-run tour: three steps over the real interface, then a card that is not a step.
struct TourOverlay: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    let targets: [String: Anchor<CGRect>]
    @State private var step = 0

    private static let steps: [(target: String, title: String, body: String)] = [
        ("connect", "tour_connect_title", "tour_connect_body"),
        ("configs", "tour_configuration_title", "tour_configuration_body"),
        ("settings", "tour_settings_title", "tour_settings_body"),
    ]

    var body: some View {
        if model.loaded, model.prefs.onboardingDone, !model.prefs.tourDone {
            GeometryReader { proxy in
                let t = model.t
                let done = step >= Self.steps.count
                ZStack(alignment: .bottom) {
                    if !done, let anchor = targets[Self.steps[step].target] {
                        let rect = proxy[anchor].insetBy(dx: -6, dy: -6)
                        RoundedRectangle(cornerRadius: 18).stroke(theme.primary, lineWidth: 2.5)
                            .frame(width: rect.width, height: rect.height).position(x: rect.midX, y: rect.midY)
                            .allowsHitTesting(false)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text(done ? t.t("tour_done_title") : t.t(Self.steps[step].title)).vText(17, 650)
                        Text(done ? t.t("mac_tour_done_body") : t.t(Self.steps[step].body)).vText(13.5, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                        HStack {
                            if done {
                                Spacer()
                                VButton(title: t.t("tour_done_guides"), variant: .text, compact: true) { finish(); nav.push(.guides) }
                                VButton(title: t.t("tour_done_dismiss"), compact: true) { finish() }
                            } else {
                                Text(t.t("tour_progress", step + 1, Self.steps.count)).vText(12.5, 500, \.muted)
                                    .accessibilityLabel(t.t("tour_progress_a11y", step + 1, Self.steps.count))
                                Spacer()
                                VButton(title: t.t("tour_skip"), variant: .text, compact: true) { finish() }
                                if step > 0 { VButton(title: t.t("tour_back"), variant: .secondary, compact: true) { step -= 1 } }
                                VButton(title: t.t("tour_next"), compact: true) { step += 1 }
                            }
                        }
                    }
                    .padding(18).frame(maxWidth: 460)
                    .background(RoundedRectangle(cornerRadius: Radius.xl).fill(theme.elevated))
                    .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(theme.border, lineWidth: 1))
                    .shadow(color: .black.opacity(0.3), radius: 18, y: 6)
                    .padding(16)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }
        }
    }

    private func finish() { model.setPrefs { $0.tourDone = true } }
}
