import AppKit
import SwiftUI
import UserNotifications
import VazieCore

func openLink(_ url: String) {
    if let target = URL(string: url) { NSWorkspace.shared.open(target) }
}

struct SettingsScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    @State private var askUninstall = false
    @State private var deleteConfigurations = false
    @State private var removing = false

    var body: some View {
        let t = model.t
        let prefs = model.prefs
        let split = prefs.split
        let splitStatus =
            !split.isActive
            ? t.t("x_split_status_off")
            : split.mode == .exclude ? t.n("x_split_status_exclude", split.sites.count) : t.n("x_split_status_include", split.sites.count)
        let feedback = model.links?.links(in: .feedback).first
        let support = model.links?.support
        let engineSubtitle: String = {
            switch model.engine {
            case let .ready(version): return t.t("mac_engine_row_ready", version)
            case .missing: return t.t("mac_engine_row_missing")
            case .installing: return t.t("x_engine_installing")
            case .unknown: return t.t("mac_engine_row_checking")
            }
        }()
        VScreen(title: t.t("settings_title"), onBack: { nav.home() }) {
            VSection(title: t.t("settings_look_and_feel"))
            VCard {
                VRow(
                    icon: .paintpalette, title: t.t("settings_appearance"),
                    subtitle: "\(t.t("settings_appearance_\(prefs.appearance.rawValue)")) · \(t.t("settings_app_icon_\(prefs.appIcon.rawValue)"))", chevron: true
                ) { nav.push(.appearance) }
            }
            VTip(id: "appearance", title: "guidance_appearance_title", message: "mac_guidance_appearance_body")

            VSection(title: t.t("mac_general_section"))
            LoginItemRow()

            VSection(title: t.t("settings_vpn_behavior"))
            VList {
                VRow(icon: .network, title: t.t("mac_mode_row"), subtitle: t.t(prefs.mode == .wholeMac ? "mac_mode_whole" : "mac_mode_proxy"), chevron: true) { nav.push(.mode) }
                VRow(icon: .split, title: t.t("settings_split_tunneling"), subtitle: splitStatus, chevron: true) { nav.push(.split) }
                VRow(icon: .engine, title: t.t("mac_engine_row"), subtitle: engineSubtitle, chevron: true) { nav.push(.engine) }
            }
            VCard {
                PreferenceToggleRow(
                    icon: .refresh, title: t.t("mac_reconnect_row"), subtitle: t.t("mac_reconnect_row_body"), isOn: prefs.autoReconnect
                ) { value in model.setPrefs { $0.autoReconnect = value } }
                VDivider()
                PreferenceToggleRow(
                    icon: .lock, title: t.t("mac_kill_switch_row"),
                    subtitle: prefs.mode == .wholeMac ? t.t("mac_kill_switch_row_body") : t.t("mac_kill_switch_proxy_mode"),
                    isOn: prefs.killSwitch && prefs.mode == .wholeMac, enabled: prefs.mode == .wholeMac
                ) { value in model.setPrefs { $0.killSwitch = value } }
            }
            .padding(.top, 8)
            VNote(text: t.t("mac_kill_switch_note"))
            VTip(id: "split", title: "guidance_split_title", message: "mac_guidance_split_body")

            VSection(title: t.t("settings_help_section"))
            VList {
                VRow(icon: .book, title: t.t("settings_guides_row"), subtitle: t.t("settings_guides_row_body"), chevron: true) { nav.push(.guides) }
                VRow(icon: .help, title: t.t("settings_faq_row"), subtitle: t.t("settings_faq_row_body"), chevron: true) { nav.push(.faq) }
                if let feedback {
                    VRow(icon: .bug, title: t.t("settings_feedback_row"), subtitle: t.t("settings_feedback_row_body"), chevron: true) { openLink(feedback.url) }
                }
                if let support {
                    VRow(icon: .mail, title: t.t("settings_contact_support"), subtitle: t.t("settings_contact_support_body"), chevron: true) { openLink(support.url) }
                }
            }
            VTip(id: "guides", title: "guidance_guides_title", message: "mac_guidance_guides_body")
            if feedback != nil { VTip(id: "bug", title: "guidance_bug_title", message: "mac_guidance_bug_body") }

            VSection(title: t.t("settings_about_section"))
            VList {
                VRow(icon: .info, title: t.t("settings_about_row"), subtitle: t.t("settings_about_row_body"), chevron: true) { nav.push(.about) }
                VRow(title: t.t("settings_check_updates"), subtitle: t.t("mac_check_updates_body", Config.version)) {
                    openLink("https://github.com/vazie-app/VazieVPN/releases/latest")
                }
            }
            VSection(title: t.t("mac_uninstall_section"))
            VCard { VRow(icon: .trash, title: t.t("mac_uninstall_row"), subtitle: t.t("mac_uninstall_row_body"), destructive: true) { askUninstall = true } }
            Text("\(t.t("settings_footer_name")) · \(Config.version)\n\(t.t("settings_footer_made_by"))")
                .vText(12, 400, \.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 22)
        }
        .overlay {
            if askUninstall {
                VDialog(
                    title: t.t("mac_uninstall_title"), message: t.t("mac_uninstall_body"),
                    confirm: t.t(removing ? "mac_uninstall_removing" : "mac_uninstall_confirm"), cancel: t.t("config_delete_cancel"), destructive: true,
                    confirmDisabled: removing,
                    onConfirm: { uninstall() }, onCancel: { if !removing { askUninstall = false } },
                    content: {
                        Toggle(isOn: $deleteConfigurations) { Text(t.t("mac_uninstall_delete_configs")).vText(14, 500) }
                            .toggleStyle(.checkbox)
                        VNote(text: t.t("mac_uninstall_list"))
                    })
            }
        }
        .task { await model.refreshLinks() }
    }

    /// The window goes with the rest: the tunnel and the Mac's network settings are put right first, everything the app
    /// keeps is removed, the bundle goes to the Trash, and the app quits.
    private func uninstall() {
        removing = true
        let keep = !deleteConfigurations
        Task {
            await model.shutdown()
            _ = await Uninstaller.run(options: Uninstaller.Options(keepConfigurations: keep, quitRunningCopies: true))
            Uninstaller.sweepAfterExit(keepConfigurations: keep)
            exit(0)
        }
    }
}

struct AppearanceScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel

    private static let swatch: [Appearance: [String]] = [.nightIndigo: ["0D1020", "9DB0FF", "F1E3C8"], .milk: ["FAF8F4", "4A5BC4", "1D1F3A"]]

    var body: some View {
        let t = model.t
        let prefs = model.prefs
        let plates = Icons.plates(for: prefs.appIcon)
        VScreen(title: t.t("settings_appearance_title")) {
            VSection(title: t.t("settings_theme"))
            HStack(spacing: 12) {
                ForEach(Appearance.allCases, id: \.self) { id in
                    let colors = Self.swatch[id]!
                    Button { model.setPrefs { $0.appearance = id } } label: {
                        VStack(spacing: 10) {
                            HStack(spacing: 6) {
                                ForEach(colors.dropFirst(), id: \.self) { Circle().fill(Color(hex: $0)).frame(width: 18, height: 18) }
                            }
                            .frame(maxWidth: .infinity, minHeight: 64).background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: colors[0])))
                            Text(t.t("settings_appearance_\(id.rawValue)")).vText(14, 600)
                        }
                        .padding(10).background(RoundedRectangle(cornerRadius: Radius.lg).fill(theme.surface))
                        .overlay(RoundedRectangle(cornerRadius: Radius.lg).stroke(prefs.appearance == id ? theme.primary : theme.border, lineWidth: prefs.appearance == id ? 2.5 : 1))
                    }
                    .buttonStyle(.plain).accessibilityAddTraits(prefs.appearance == id ? .isSelected : [])
                }
            }

            VSection(title: t.t("settings_app_icon"))
            VBody(text: t.t("mac_app_icon_body")).padding(.bottom, 8)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: 10)], spacing: 10) {
                ForEach(AppIconId.allCases, id: \.self) { icon in
                    let plate = Icons.plate(prefs.appIconPlate, for: icon)
                    Button { model.setPrefs { $0.appIcon = icon; $0.appIconPlate = plate } } label: {
                        VStack(spacing: 6) {
                            IconPreview(icon: icon, plate: plate, size: 56)
                            Text(t.t("settings_app_icon_\(icon.rawValue)")).vText(12, 500, prefs.appIcon == icon ? \.primary : \.text2).lineLimit(1)
                        }
                        .padding(8).frame(maxWidth: .infinity)
                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(prefs.appIcon == icon ? theme.accentContainer : .clear))
                        .overlay(RoundedRectangle(cornerRadius: Radius.md).stroke(prefs.appIcon == icon ? theme.primary : theme.border, lineWidth: prefs.appIcon == icon ? 2 : 1))
                    }
                    .buttonStyle(.plain).accessibilityAddTraits(prefs.appIcon == icon ? .isSelected : [])
                }
            }

            VSection(title: t.t("settings_app_icon_plate"))
            HStack(spacing: 12) {
                ForEach(plates, id: \.self) { plate in
                    Button { model.setPrefs { $0.appIconPlate = plate } } label: {
                        VStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(LinearGradient(colors: (Icons.plateColors[plate] ?? ["#12111A"]).map { Color(hex: $0) } + ((Icons.plateColors[plate]?.count ?? 0) == 1 ? [Color(hex: Icons.plateColors[plate]![0])] : []), startPoint: .topLeading, endPoint: .bottomTrailing))
                                .frame(width: 52, height: 52)
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(prefs.appIconPlate == plate ? theme.primary : theme.border, lineWidth: prefs.appIconPlate == plate ? 3 : 1))
                            Text(t.t("settings_app_icon_plate_\(plate.rawValue)")).vText(12, 500, prefs.appIconPlate == plate ? \.primary : \.text2)
                        }
                    }
                    .buttonStyle(.plain).accessibilityAddTraits(prefs.appIconPlate == plate ? .isSelected : [])
                }
            }
        }
    }
}

/// The Xray engine: what it is, whether it is on this Mac, and how to get it. The extension's "Helper" screen, for
/// an app that runs the engine itself.
struct EngineScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @State private var failed = false

    var body: some View {
        let t = model.t
        VScreen(title: t.t("mac_engine_row")) {
            VBody(text: t.t("mac_engine_why"))
            VSection(title: t.t("mac_engine_status"))
            VCard {
                switch model.engine {
                case let .ready(version):
                    VDetailRow(label: t.t("mac_engine_status"), value: t.t("mac_engine_ready"), mono: false)
                    VDetailRow(label: "Xray", value: version)
                    VDetailRow(label: t.t("mac_engine_source"), value: Engine.binary()?.path.hasPrefix(Bundle.main.bundlePath) == true ? t.t("mac_engine_source_bundled") : t.t("mac_engine_source_downloaded"), mono: false)
                case let .installing(done, total):
                    VStack(alignment: .leading, spacing: 8) {
                        Text(t.t("x_engine_installing")).vText(14, 600)
                        ProgressView(value: total > 0 ? Double(done) / Double(total) : nil).tint(theme.primary)
                        if total > 0 { Text("\(amount(done, t)) / \(amount(total, t))").font(Fonts.mono(12)).foregroundStyle(theme.text2) }
                    }
                    .padding(16)
                case .missing, .unknown:
                    VDetailRow(label: t.t("mac_engine_status"), value: t.t("mac_engine_none"), mono: false)
                }
            }
            if failed { VBanner(tone: .error, message: t.t("x_engine_failed")) }
            if case .installing = model.engine {
            } else {
                VButton(title: t.t(isReady ? "mac_engine_reinstall" : "x_engine_install"), variant: isReady ? .outline : .primary, icon: .refresh) {
                    Task { failed = !(await model.installEngine()) }
                }
                .padding(.top, 16)
            }
            VNote(text: t.t("mac_engine_pinned", Engine.version))
        }
        .task { await model.refreshEngine() }
    }

    private var isReady: Bool {
        if case .ready = model.engine { return true }
        return false
    }
}

// MARK: help

struct GuidesScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    var body: some View {
        let t = model.t
        let viewed = Set(model.prefs.viewedGuides)
        VScreen(title: t.t("settings_guides_title")) {
            VBody(text: t.t("settings_guides_body"))
            VSection(title: t.t("getting_started_section"))
            VList {
                VRow(icon: .book, title: t.t("getting_started_first_config_short"), subtitle: t.t("getting_started_first_config_summary"), chevron: true) { nav.push(.guide("first-config")) }
                VRow(icon: .book, title: t.t("getting_started_manage_short"), subtitle: t.t("getting_started_manage_summary"), chevron: true) { nav.push(.guide("manage")) }
                VRow(icon: .engine, title: t.t("mac_engine_row"), subtitle: t.t("mac_guide_engine_summary"), chevron: true) { nav.push(.engine) }
            }
            VSection(title: t.t("settings_guides_system_section"))
            VList {
                ForEach(GuideId.allCases, id: \.self) { guide in
                    VRow(icon: icon(guide), title: t.t("mac_guide_\(guide.rawValue)_title"), subtitle: t.t("mac_guide_\(guide.rawValue)_summary"), chevron: true, action: { nav.push(.guide(guide.rawValue)) }) {
                        if viewed.contains(guide) { Text(t.t("settings_guides_viewed")).vText(12, 500, \.muted) }
                    }
                }
            }
            VCard {
                VRow(icon: .refresh, title: t.t("settings_guides_replay_tour"), subtitle: t.t("settings_guides_replay_tour_body")) {
                    model.setPrefs { $0.tourDone = false }
                    nav.home()
                }
            }
            .padding(.top, 20)
        }
    }

    private func icon(_ guide: GuideId) -> VIcon {
        switch guide {
        case .menuBar: return .menubar
        case .dock: return .dock
        case .systemProxy: return .network
        case .notification: return .bell
        }
    }
}

struct GuideDetailScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    let id: String

    private static let steps: [GuideId: Int] = [.menuBar: 3, .dock: 2, .systemProxy: 3, .notification: 3]

    var body: some View {
        let t = model.t
        Group {
            if id == "first-config" {
                VScreen(title: t.t("getting_started_first_config_title")) {
                    VBody(text: t.t("getting_started_first_config_intro"))
                    VHeading(text: t.t("getting_started_shape_heading"))
                    VBody(text: t.t("getting_started_shape_body"))
                    VExample(text: exampleLink).accessibilityLabel(t.t("getting_started_example_a11y")).padding(.top, 6)
                    VHeading(text: t.t("getting_started_reads_heading"))
                    VBody(text: t.t("getting_started_reads_body", "VLESS"))
                    VButton(title: t.t("getting_started_add_action")) { nav.push(.add) }.padding(.top, 20)
                }
            } else if id == "manage" {
                VScreen(title: t.t("getting_started_manage_title")) {
                    VBody(text: t.t("getting_started_manage_intro"))
                    VHeading(text: t.t("getting_started_manage_list_heading"))
                    VBody(text: t.t("mac_manage_list_body"))
                    VHeading(text: t.t("getting_started_manage_details_heading"))
                    VBody(text: t.t("getting_started_manage_details_body"))
                    VHeading(text: t.t("getting_started_manage_export_heading"))
                    VBody(text: t.t("getting_started_manage_export_body"))
                }
            } else if let guide = GuideId(rawValue: id) {
                let notificationsOn = model.prefs.notificationsEnabled
                VScreen(title: t.t("mac_guide_\(guide.rawValue)_title")) {
                    VBody(text: t.t(guide == .notification ? (notificationsOn ? "mac_guide_notification_body_on" : "mac_guide_notification_body_off") : "mac_guide_\(guide.rawValue)_body"))
                    if guide == .notification {
                        VButton(title: t.t(notificationsOn ? "mac_guide_notification_open" : "mac_guide_notification_action")) {
                            Task {
                                let status = await Notifier.shared.status()
                                if notificationsOn || status == .denied {
                                    Notifier.shared.openSystemSettings()
                                } else {
                                    let granted = await Notifier.shared.requestAuthorization()
                                    model.setPrefs { $0.notificationsEnabled = granted; $0.notificationPromptShown = true }
                                }
                            }
                        }
                        .padding(.top, 16)
                    }
                    VSection(title: t.t("x_guide_how"))
                    VCard(padded: true) {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(1...(Self.steps[guide] ?? 1), id: \.self) { n in
                                HStack(alignment: .firstTextBaseline, spacing: 10) {
                                    Text("\(n)").font(Fonts.mono(12, 600)).foregroundStyle(theme.onAccentContainer)
                                        .frame(width: 24, height: 24).background(Circle().fill(theme.accentContainer))
                                    Text(t.t("mac_guide_\(guide.rawValue)_step_\(n)")).vText(14, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                    }
                }
                .onAppear { if !model.prefs.viewedGuides.contains(guide) { model.setPrefs { $0.viewedGuides.append(guide) } } }
            } else {
                VScreen(title: t.t("settings_guides_title")) {}
            }
        }
    }
}

/// The questions, from the backend only: the app ships none. Loading, unavailable and empty are states.
struct FaqScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @State private var faq: Backend.Faq?
    @State private var loading = true
    @State private var open: Set<String> = []

    var body: some View {
        let t = model.t
        VScreen(title: t.t("faq_title")) {
            if let faq {
                if faq.items.isEmpty {
                    VBody(text: t.t("faq_empty"))
                } else {
                    VList {
                        ForEach(faq.items, id: \.key) { item in
                            VStack(alignment: .leading, spacing: 0) {
                                VRow(title: item.question.resolve(t.language), subtitle: nil, chevron: false, action: {
                                    if open.contains(item.key) { open.remove(item.key) } else { open.insert(item.key) }
                                }) { VSymbol(icon: .chevron, size: 12).foregroundStyle(theme.muted).rotationEffect(.degrees(open.contains(item.key) ? 90 : 0)) }
                                if open.contains(item.key) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        Text(item.answer.resolve(t.language)).vText(14, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                                        if let link = item.link {
                                            Button(link.label.resolve(t.language)) { openLink(link.url.hasPrefix("/") ? Config.siteURL + link.url : link.url) }
                                                .buttonStyle(.plain).vText(14, 600, \.primary)
                                        }
                                    }
                                    .padding(.horizontal, 16).padding(.bottom, 14)
                                }
                            }
                        }
                    }
                }
            } else if loading {
                VBody(text: t.t("faq_loading"))
            } else {
                VCard(padded: true) {
                    Text(t.t("faq_unavailable")).vText(14, 400, \.text2).padding(.bottom, 12)
                    VButton(title: t.t("faq_retry"), variant: .secondary) { Task { await load(true) } }
                }
            }
        }
        .task { await load(false) }
    }

    private func load(_ force: Bool) async {
        loading = true
        faq = await model.faq(force: force).faq
        loading = false
    }
}

struct AboutScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    var body: some View {
        let t = model.t
        let links = model.links
        func section(_ s: LinkSection) -> [AppLink] { links?.links(in: s) ?? [] }
        func rows(_ list: [AppLink]) -> some View {
            VList {
                ForEach(list, id: \.key) { link in
                    VRow(title: link.title.resolve(t.language), subtitle: link.subtitle?.resolve(t.language), chevron: true) { openLink(link.url) }
                }
            }
        }
        return VScreen(title: t.t("settings_about_title")) {
            VSection(title: t.t("settings_made_by"))
            // The author's card stays even with no links: it says who makes the app.
            VCard(padded: true) {
                VStack(spacing: 6) {
                    if let sticker = IconArt.asset("vazovsky_sticker", in: "brand") {
                        Image(nsImage: sticker).resizable().scaledToFit().frame(height: 120).accessibilityLabel(t.t("vazie_a11y_vazovsky_sticker"))
                    }
                    Text(t.t("settings_made_by_title")).vText(17, 650).multilineTextAlignment(.center)
                    Text(t.t("settings_made_by_body")).vText(13, 400, \.muted).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }
            if !section(.author).isEmpty { rows(section(.author)).padding(.top, 10) }

            // The app's own page, not a link: it is here whatever the backend publishes.
            VSection(title: t.t("settings_privacy_section"))
            VCard { VRow(icon: .lock, title: t.t("settings_privacy_row"), subtitle: t.t("settings_privacy_row_body"), chevron: true) { nav.push(.privacy) } }

            if !section(.project).isEmpty { VSection(title: t.t("settings_project_section")); rows(section(.project)) }
            if !section(.security).isEmpty { VSection(title: t.t("settings_security_section")); rows(section(.security)) }
            if !section(.help).isEmpty { VSection(title: t.t("settings_about_help_section")); rows(section(.help)) }
            if !section(.support).isEmpty {
                VSection(title: t.t("settings_support_section"))
                ForEach(section(.support), id: \.key) { link in
                    VCard(padded: true) {
                        Text(link.title.resolve(t.language)).vText(16, 650)
                        if let sub = link.subtitle { Text(sub.resolve(t.language)).vText(13, 400, \.muted).padding(.top, 2).padding(.bottom, 10) }
                        VButton(title: (link.action ?? link.title).resolve(t.language), variant: .secondary) { openLink(link.url) }
                    }
                    .padding(.top, 8)
                }
            }
            if !section(.feedback).isEmpty { VSection(title: t.t("settings_feedback_section")); rows(section(.feedback)) }
            Text(t.t("mac_build_signature", Config.version, Config.build)).vText(12, 400, \.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.top, 22)
        }
        .task { await model.refreshLinks() }
    }
}

struct PrivacyScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel

    private static let sections: [(title: String, items: [String])] = [
        ("privacy_stores_title", ["mac_privacy_stores_configs", "privacy_stores_choices", "privacy_stores_usage"]),
        ("privacy_where_title", ["mac_privacy_where_encrypted", "mac_privacy_where_plain", "mac_privacy_where_sync"]),
        ("privacy_never_title", ["privacy_never_analytics", "privacy_never_account", "privacy_never_browsing"]),
        ("privacy_logs_title", ["mac_privacy_logs_body"]),
        ("privacy_vpn_title", ["mac_privacy_vpn_body", "mac_privacy_vpn_dns"]),
        ("mac_privacy_engine_title", ["mac_privacy_engine_local", "mac_privacy_engine_download"]),
        ("mac_privacy_system_title", ["mac_privacy_system_body", "mac_privacy_system_restore"]),
        ("privacy_own_title", ["privacy_own_body"]),
        ("mac_privacy_surfaces_title", ["mac_privacy_surfaces_menu", "mac_privacy_surfaces_notification", "mac_privacy_surfaces_network"]),
        ("privacy_export_title", ["mac_privacy_export_body"]),
    ]

    var body: some View {
        let t = model.t
        VScreen(title: t.t("settings_privacy_title")) {
            VBody(text: t.t("settings_privacy_body"))
            ForEach(Self.sections, id: \.title) { section in
                VHeading(text: t.t(section.title))
                VCard(padded: true) {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(section.items, id: \.self) { item in
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text("•").foregroundStyle(theme.muted)
                                Text(t.t(item)).vText(13.5, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
            }
        }
    }
}

/// First run, in four pages, narrated by Vazi.
struct OnboardingScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    @State private var page = 0

    private static let pages: [(title: String, body: String)] = [
        ("onboarding_welcome_title", "mac_onboarding_welcome_body"),
        ("onboarding_what_title", "mac_onboarding_what_body"),
        ("onboarding_own_title", "mac_onboarding_own_body"),
        ("onboarding_reach_title", "mac_onboarding_reach_body"),
    ]

    var body: some View {
        let t = model.t
        let last = page == Self.pages.count - 1
        VStack(spacing: 0) {
            VStack(spacing: 16) {
                IconPreview(icon: model.prefs.appIcon, plate: model.prefs.appIconPlate, size: 120).padding(.bottom, 12)
                Text(t.t(Self.pages[page].title)).vText(28, 650).multilineTextAlignment(.center)
                Text(t.t(Self.pages[page].body)).vText(15.5, 400, \.text2).multilineTextAlignment(.center).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: 440).padding(.horizontal, 24).frame(maxHeight: .infinity)
            .id(page).transition(.opacity)
            VStack(spacing: 10) {
                HStack(spacing: 8) {
                    ForEach(0..<Self.pages.count, id: \.self) { i in
                        Capsule().fill(i == page ? theme.primary : theme.border).frame(width: i == page ? 22 : 8, height: 8)
                    }
                }
                .accessibilityElement().accessibilityLabel(t.t("onboarding_page_indicator", page + 1, Self.pages.count)).padding(.bottom, 8)
                if last {
                    VButton(title: t.t("onboarding_add_config")) { finish(add: true) }
                    VButton(title: t.t("onboarding_start"), variant: .text) { finish(add: false) }
                } else {
                    VButton(title: t.t("onboarding_next")) { withAnimation { page += 1 } }.keyboardShortcut(.defaultAction)
                    VButton(title: t.t("onboarding_skip"), variant: .text) { finish(add: false) }
                }
            }
            .frame(maxWidth: 440).padding(.horizontal, 24).padding(.bottom, 28)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(theme.background)
    }

    private func finish(add: Bool) {
        model.setPrefs { $0.onboardingDone = true }
        if add { nav.replaceAll(.add) } else { nav.home() }
    }
}

/// Whole Mac or system proxy.
struct ModeScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel

    var body: some View {
        let t = model.t
        VScreen(title: t.t("mac_mode_title")) {
            VList {
                ForEach(ConnectionMode.allCases, id: \.self) { mode in
                    let key = mode == .wholeMac ? "mac_mode_whole" : "mac_mode_proxy"
                    VRow(title: t.t(key), subtitle: t.t(key + "_body"), selected: model.prefs.mode == mode) { model.setPrefs { $0.mode = mode } }
                }
            }
            .padding(.top, 8)
            VNote(text: t.t("mac_mode_note"))
        }
    }
}

/// A switch with a title and a sentence under it, in the card style of the settings.
struct PreferenceToggleRow: View {
    @Environment(\.theme) var theme
    let icon: VIcon
    let title: String
    let subtitle: String
    let isOn: Bool
    var enabled = true
    let change: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            VSymbol(icon: icon, size: 18).foregroundStyle(enabled ? theme.primary : theme.muted).frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).vText(15, 550)
                Text(subtitle).vText(12.5, 400, \.text2).fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Toggle("", isOn: Binding(get: { isOn }, set: change)).toggleStyle(.switch).labelsHidden().disabled(!enabled)
                .accessibilityLabel(title)
        }
        .padding(.horizontal, 16).padding(.vertical, 13)
    }
}

/// "Launch at login": a switch that follows what the system says, and the way to its approval when it asks for one.
struct LoginItemRow: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    @State private var state = LoginItem.state

    var body: some View {
        let t = model.t
        VCard {
            HStack(spacing: 12) {
                VSymbol(icon: .power, size: 18).foregroundStyle(theme.primary).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(t.t("mac_login_row")).vText(15, 550)
                    Text(t.t(state == .needsApproval ? "mac_login_needs_approval" : state == .unavailable ? "mac_login_unavailable" : "mac_login_row_body"))
                        .vText(12.5, 400, \.text2).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 8)
                Toggle("", isOn: Binding(get: { state == .on || state == .needsApproval }, set: { change($0) }))
                    .toggleStyle(.switch).labelsHidden().disabled(state == .unavailable)
                    .accessibilityLabel(t.t("mac_login_row"))
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            if state == .needsApproval {
                VDivider()
                VRow(title: t.t("mac_login_open_settings"), chevron: true) { LoginItem.openSystemSettings() }
            }
        }
        .onAppear { state = LoginItem.state }
        // The person may change it in System Settings while this screen is open.
        .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in state = LoginItem.state }
    }

    private func change(_ on: Bool) {
        if !LoginItem.set(on) { nav.show(model.t.t("mac_login_failed")) }
        state = LoginItem.state
    }
}
