import AppKit
import SwiftUI
import VazieCore

func lastUsedText(_ t: Translator, _ used: Format.LastUsed) -> String {
    func two(_ n: Int) -> String { n < 10 ? "0\(n)" : "\(n)" }
    switch used {
    case .never: return t.t("config_last_used_never")
    case .justNow: return t.t("config_last_used_just_now")
    case let .today(hour, minute): return t.t("config_last_used_today", "\(two(hour)):\(two(minute))")
    case .yesterday: return t.t("config_last_used_yesterday")
    case let .earlier(year, month, day): return t.t("config_last_used_earlier", "\(two(day)).\(two(month)).\(year)")
    }
}

/// Puts a secret on the pasteboard and takes it back after thirty seconds, if nothing else was copied meanwhile.
@MainActor
func copySecret(_ text: String) {
    let board = NSPasteboard.general
    board.clearContents()
    board.setString(text, forType: .string)
    // Marked so that clipboard managers do not keep it.
    board.setData(Data(), forType: NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
    let count = board.changeCount
    DispatchQueue.main.asyncAfter(deadline: .now() + 30) {
        if NSPasteboard.general.changeCount == count { NSPasteboard.general.clearContents() }
    }
}

struct ConfigDetailsScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator
    let id: String
    @State private var details: ProfileDetails?
    @State private var missing = false
    @State private var secret: String?
    @State private var rename: String?
    @State private var exporting = false
    @State private var deleting = false

    var body: some View {
        let t = model.t
        ZStack {
            VScreen(title: details?.name ?? t.t("config_section_configuration")) {
                if let d = details {
                    VSection(title: t.t("config_section_configuration"))
                    VCard {
                        VDetailRow(label: t.t("config_field_protocol"), value: [d.protocolLabel, ConfigPreview.securityLabel(d.security)].compactMap { $0 }.joined(separator: " · "))
                        VDetailRow(label: t.t("config_field_transport"), value: [ConfigPreview.transportLabel(d.transport), d.transportDetail].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                        VDetailRow(label: t.t("config_field_server"), value: "\(d.endpointHost):\(d.endpointPort)")
                        if let sni = d.serverName { VDetailRow(label: t.t("config_field_sni"), value: sni) }
                        if let fp = d.fingerprint { VDetailRow(label: t.t("config_field_fingerprint"), value: fp) }
                        if let flow = d.flow { VDetailRow(label: t.t("config_field_flow"), value: flow.rawValue) }
                        VDetailRow(label: t.t("config_field_last_used"), value: lastUsedText(t, Format.lastUsed(d.lastUsedAt, now: nowMillis())), mono: false)
                    }
                    VSection(title: t.t("config_section_credentials"))
                    VCard {
                        VDetailRow(
                            label: "UUID", value: secret ?? "••••••••-••••-••••-••••-••••••••••••",
                            trailing: AnyView(
                                Button(t.t(secret != nil ? "config_secret_hide" : "config_secret_show")) {
                                    Task { secret = secret != nil ? nil : await model.revealCredential(id) }
                                }.buttonStyle(.plain).vText(13, 600, \.primary)))
                    }
                    if !d.unknownParameterNames.isEmpty {
                        VNote(text: t.t("config_review_kept_title", d.unknownParameterNames.joined(separator: ", ")) + "\n" + t.t("config_review_kept_body"))
                    }
                    VSection(title: t.t("config_section_actions"))
                    VList {
                        VRow(icon: .edit, title: t.t("config_action_rename")) { rename = d.name }
                        VRow(icon: .copy, title: t.t("config_action_duplicate")) {
                            Task {
                                _ = await model.duplicate(id, name: t.t("config_duplicate_name", d.name))
                                nav.show(t.t("config_duplicated"))
                            }
                        }
                        VRow(icon: .share, title: t.t("config_action_export")) { exporting = true }
                    }
                    VSection(title: t.t("config_section_danger"))
                    VCard { VRow(icon: .trash, title: t.t("config_action_delete"), destructive: true) { deleting = true } }
                } else if missing {
                    VBody(text: t.t("config_details_missing"))
                }
            }
            if let current = rename, let d = details {
                VDialog(
                    title: t.t("config_rename_title"), confirm: t.t("config_rename_confirm"), cancel: t.t("config_rename_cancel"),
                    confirmDisabled: current.trimmingCharacters(in: .whitespaces).isEmpty,
                    onConfirm: {
                        let name = current.trimmingCharacters(in: .whitespaces)
                        rename = nil
                        Task { await model.rename(d.id, to: name) }
                    }, onCancel: { rename = nil },
                    content: {
                        VTextField(label: t.t("config_field_name"), text: Binding(get: { rename ?? "" }, set: { rename = $0 }))
                        VNote(text: t.t("config_rename_body"))
                    })
            }
            if exporting {
                VDialog(
                    title: t.t("config_export_title"), confirm: t.t("x_export_confirm"), cancel: t.t("config_export_cancel"),
                    onConfirm: {
                        exporting = false
                        Task {
                            if let link = await model.exportLink(id) {
                                copySecret(link)
                                nav.show(t.t("x_link_copied"))
                            }
                        }
                    }, onCancel: { exporting = false },
                    content: {
                        VBanner(tone: .warning, message: t.t("config_export_warning"))
                        VNote(text: t.t("config_export_body"))
                    })
            }
            if deleting, let d = details {
                VDialog(
                    title: t.t("config_delete_title"), message: t.t("config_delete_body", d.name), confirm: t.t("config_delete_confirm"),
                    cancel: t.t("config_delete_cancel"), destructive: true,
                    onConfirm: {
                        deleting = false
                        Task {
                            await model.remove(id)
                            nav.pop()
                        }
                    }, onCancel: { deleting = false })
            }
        }
        // Read again whenever the list changes: a rename, or a connection that has just marked it used.
        .task(id: model.profiles) {
            details = await model.details(id)
            missing = details == nil
        }
        // A revealed credential does not outlive the screen or a minute's looking away.
        .task(id: secret) {
            guard secret != nil else { return }
            try? await Task.sleep(nanoseconds: 60_000_000_000)
            if !Task.isCancelled { secret = nil }
        }
        .onDisappear { secret = nil }
    }
}

/// The technical details of the live session, as rows. Opened from the traffic line on Home.
struct DetailsScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @State private var rows: [Diagnose.Row] = []
    @State private var checking = false

    var body: some View {
        let t = model.t
        VScreen(title: t.t("home_details_title")) {
            if case .connected = model.snapshot.state, let d = model.snapshot.diagnostics {
                VCard {
                    VDetailRow(label: t.t("home_diagnostics_engine"), value: d.engine)
                    VDetailRow(label: t.t("home_diagnostics_protocol"), value: d.protocolId)
                    VDetailRow(label: t.t("home_diagnostics_security"), value: d.security.rawValue)
                    VDetailRow(label: t.t("home_diagnostics_transport"), value: d.transport)
                    if let flow = d.flow { VDetailRow(label: t.t("home_diagnostics_flow"), value: flow.rawValue) }
                    VDetailRow(label: t.t("home_diagnostics_endpoint"), value: d.endpointHost)
                    VDetailRow(label: t.t("home_diagnostics_port"), value: String(d.endpointPort))
                    VDetailRow(label: t.t("home_diagnostics_latency"), value: d.latencyMs.map { t.t("home_value_milliseconds", $0) } ?? t.t("home_value_unknown"))
                    VDetailRow(label: t.t("home_diagnostics_traffic"), value: t.t("home_value_traffic", amount(model.traffic?.rx, t), amount(model.traffic?.tx, t)))
                    VDetailRow(label: t.t("home_diagnostics_dns"), value: d.dnsMode)
                    VDetailRow(label: t.t("home_diagnostics_ip"), value: t.t("home_value_traffic", t.t(d.ipv4 ? "home_value_on" : "home_value_off"), t.t(d.ipv6 ? "home_value_on" : "home_value_off")))
                    VDetailRow(label: t.t("mac_diagnostics_route"), value: model.prefs.mode == .wholeMac ? t.t("mac_route_tun") : (model.prefs.split.isActive ? t.t("mac_route_script") : t.t("mac_route_direct")), mono: false)
                }
                VButton(title: t.t(checking ? "mac_check_running" : "mac_check_run"), variant: .secondary, icon: .refresh) {
                    checking = true
                    Task {
                        rows = await model.runDiagnostics()
                        checking = false
                    }
                }
                .disabled(checking).padding(.top, 16)
                if !rows.isEmpty {
                    ForEach([Diagnose.Row.Route.tunnel, .system], id: \.self) { route in
                        VSection(title: t.t(route == .tunnel ? "mac_check_via_tunnel" : "mac_check_via_system"))
                        VCard {
                            ForEach(rows.filter { $0.route == route }) { row in
                                VDetailRow(
                                    label: row.site.hasSuffix("/trace") ? t.t("mac_check_exit") : row.site.replacingOccurrences(of: "/generate_204", with: ""),
                                    value: row.ms.map { ms in row.detail.map { "\($0) · \(ms) ms" } ?? "\(ms) ms" } ?? t.t("mac_check_failed", row.failure ?? "?"))
                            }
                        }
                    }
                    VNote(text: t.t("mac_check_note"))
                }
            } else {
                VCard(padded: true) {
                    Text(t.t("home_details_empty_title")).vText(15, 650)
                    Text(t.t("home_details_empty_body")).vText(13, 400, \.text2).padding(.top, 4)
                }
            }
        }
        .onAppear { model.beginWatching() }
        .onDisappear { model.endWatching() }
    }
}

/// Split tunneling by site (`SplitTunnelScreen.kt`, with sites for apps).
struct SplitScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @State private var input = ""
    @State private var invalid = false

    var body: some View {
        let t = model.t
        let split = model.prefs.split
        VScreen(title: t.t("split_tunnel_title")) {
            VBanner(message: t.t("mac_split_note") + (model.prefs.mode == .wholeMac ? " " + t.t("mac_split_note_whole") : ""))
            VList {
                ForEach(SplitMode.allCases, id: \.self) { mode in
                    VRow(title: t.t("x_split_mode_\(mode.rawValue)"), subtitle: t.t("mac_split_mode_\(mode.rawValue)_body"), selected: split.mode == mode) {
                        Task { await model.setSplit(SplitTunnel(mode: mode, sites: split.sites)) }
                    }
                }
            }
            .padding(.top, 14)

            if split.mode != .off {
                VSection(title: t.n("x_split_selected", split.sites.count))
                HStack(alignment: .top, spacing: 8) {
                    VTextField(
                        label: "", text: Binding(get: { input }, set: { input = $0; invalid = false }), placeholder: t.t("x_split_add_placeholder"), mono: true,
                        error: invalid ? t.t("x_split_invalid") : nil, onSubmit: add)
                    VButton(title: t.t("x_split_add"), variant: .secondary, compact: true, action: add)
                        .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty).padding(.top, 22)
                }
                if split.sites.isEmpty {
                    VNote(text: t.t("x_split_empty"))
                } else {
                    FlowLayout(spacing: 8) {
                        ForEach(split.sites, id: \.self) { site in
                            HStack(spacing: 6) {
                                Text(site).font(Fonts.mono(12)).foregroundStyle(theme.onAccentContainer)
                                Button { Task { await model.setSplit(split.removing(site)) } } label: { VSymbol(icon: .close, size: 10, weight: .bold) }
                                    .buttonStyle(.plain).foregroundStyle(theme.onAccentContainer).accessibilityLabel(t.t("x_split_remove", site))
                            }
                            .padding(.horizontal, 12).padding(.vertical, 7).background(Capsule().fill(theme.accentContainer))
                        }
                    }
                    .padding(.top, 14)
                }
                if split.sites.count >= SplitTunnel.maxSites { VNote(text: t.t("x_split_full")) }
            }
        }
    }

    private func add() {
        let split = model.prefs.split
        guard let next = split.adding(input) else {
            invalid = true
            return
        }
        input = ""
        Task { await model.setSplit(next) }
    }
}

/// Chips that wrap onto as many lines as they need.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(proposal.width ?? 400, subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(bounds.width, subviews)
        for (index, origin) in result.origins.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y), proposal: .unspecified)
        }
    }

    private func arrange(_ width: CGFloat, _ subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        var origins: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: maxX, height: y + rowHeight), origins)
    }
}
