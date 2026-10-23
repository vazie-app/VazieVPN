import AppKit
import SwiftUI
import VazieCore

func statusWord(_ t: Translator, _ state: ConnectionState, hasProfiles: Bool) -> String {
    switch state {
    case .connected: return t.t("connection_status_protected")
    case .preparing: return t.t("connection_status_preparing")
    case .connecting: return t.t("connection_status_connecting")
    case .disconnecting: return t.t("connection_status_disconnecting")
    case .reconnecting: return t.t("mac_status_reconnecting")
    case let .lost(_, blocked): return t.t(blocked ? "mac_status_lost_blocked" : "mac_status_lost_open")
    case .failed: return t.t("connection_status_failed")
    case .noInternet: return t.t("connection_status_no_internet")
    case .idle: return hasProfiles ? t.t("connection_status_disconnected") : t.t("connection_status_no_configuration")
    }
}

func dot(for state: ConnectionState) -> IconArt.Dot? {
    switch state {
    case .connected: return .connected
    case .preparing, .connecting, .disconnecting, .noInternet, .reconnecting: return .busy
    case .failed, .lost: return .failed
    case .idle: return nil
    }
}

/// What Android spreads over a widget and the Quick Settings tile: the state, one button, and the recent
/// configurations, from the menu bar.
struct MenuBarContent: View {
    @Environment(\.theme) var theme
    @Environment(\.openWindow) var openWindow
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    var body: some View {
        let t = model.t
        let state = model.snapshot.state
        let view = HomeView.make(snapshot: model.snapshot, profiles: model.profiles, selectedId: model.selectedId)
        let presentation = present(view)
        let recent = recentConnections(model.profiles).prefix(5)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                IconPreview(icon: model.prefs.appIcon, plate: model.prefs.appIconPlate, size: 36)
                VStack(alignment: .leading, spacing: 1) {
                    Text(statusWord(t, state, hasProfiles: !model.profiles.isEmpty)).vText(16, 650)
                    Text(subtitle(t, view, presentation)).vText(12, 400, \.text2).lineLimit(1)
                }
                Spacer()
            }
            if presentation.action == .add {
                VButton(title: t.t(presentation.actionKey), icon: .plus) { show(.add) }
            } else {
                VButton(title: t.t(presentation.actionKey), variant: variant(presentation.actionVariant), icon: .power) {
                    Task {
                        switch presentation.action {
                        case .cancel, .disconnect: await model.disconnect()
                        default: await model.connect()
                        }
                    }
                }
                .disabled(!presentation.actionEnabled)
            }
            if !recent.isEmpty {
                Text(t.t("connections_my_configs")).vText(12, 600, \.muted)
                VStack(spacing: 0) {
                    ForEach(Array(recent)) { profile in
                        Button { Task { await model.connect(profile.id) } } label: {
                            HStack(spacing: 10) {
                                Circle().fill(connected(profile.id) ? theme.success : model.selectedId == profile.id ? theme.primary : theme.border).frame(width: 8, height: 8)
                                Text(profile.name).vText(14, 550).lineLimit(1)
                                Spacer()
                                if let ms = model.latencies[profile.id] {
                                    Text(ms.map { t.t("connections_latency_ms", $0) } ?? "—").font(Fonts.mono(11)).foregroundStyle(theme.muted)
                                }
                            }
                            .padding(.vertical, 8).padding(.horizontal, 10).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain).disabled(!profile.runnable)
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12).fill(theme.surface))
            }
            if state.isConnected {
                HStack {
                    Text("↓ \(amount(model.traffic?.rx, t))").font(Fonts.mono(12)).foregroundStyle(theme.text2)
                    Spacer()
                    Text("↑ \(amount(model.traffic?.tx, t))").font(Fonts.mono(12)).foregroundStyle(theme.text2)
                }
            }
            Divider().overlay(theme.divider)
            HStack {
                Button(t.t("mac_menu_open")) { show(nil) }.buttonStyle(.plain).vText(13, 600, \.primary)
                Spacer()
                Button(t.t("mac_menu_quit")) { NSApp.terminate(nil) }.buttonStyle(.plain).vText(13, 500, \.muted)
            }
        }
        .padding(16).frame(width: 320)
        .background(theme.background)
        .onAppear { model.beginWatching(); Task { await model.measureLatencies() } }
        .onDisappear { model.endWatching() }
    }

    private func connected(_ id: String) -> Bool { model.snapshot.state.profileId == id && model.snapshot.state.isConnected }

    private func show(_ route: Route?) {
        if let route { nav.replaceAll(route) }
        openWindow(id: "main")
        NSApp.activate(ignoringOtherApps: true)
    }

    private func variant(_ v: ActionVariant) -> VButtonVariant { v == .destructive ? .destructive : v == .secondary ? .secondary : .primary }

    private func subtitle(_ t: Translator, _ view: HomeView, _ p: Presentation) -> String {
        switch p.line {
        case let .words(key): return t.t(key)
        case let .wordsNamed(key): return t.t(key, model.snapshot.subject?.name ?? "")
        case let .session(label):
            if case let .connected(_, since) = model.snapshot.state {
                return t.t("home_route_session", Format.sessionDuration(seconds: Int(Date().timeIntervalSince(since))), label)
            }
            return label
        }
    }
}
