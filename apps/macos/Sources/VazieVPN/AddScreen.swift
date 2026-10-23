import AppKit
import SwiftUI
import VazieCore

/// The example a person is shown: the one configuration-shaped string that is nobody's.
let exampleLink = "vless://00000000-0000-0000-0000-000000000001@vpn.example.com:443?..."

enum Detection: Equatable {
    case waiting
    case recognized(ConfigPreview)
    case unsupported(UnsupportedFeature)
    case invalid(InvalidReason)

    static func of(_ text: String) -> Detection {
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return .waiting }
        switch Parsers.parse(text) {
        case let .recognized(draft): return .recognized(ConfigPreview(draft: draft))
        case let .unsupported(feature): return .unsupported(feature)
        case let .invalid(reason): return .invalid(reason)
        }
    }
}

/// Every parser outcome as a sentence a person can act on (`ImportMessages.kt`).
func invalidMessage(_ t: Translator, _ reason: InvalidReason) -> String {
    switch reason {
    case .empty: return t.t("config_invalid_empty")
    case let .unknownScheme(scheme): return scheme.map { t.t("config_invalid_other_scheme", $0) } ?? t.t("config_invalid_not_a_link")
    case .malformedURI: return t.t("config_invalid_malformed")
    case .missingUserID: return t.t("config_invalid_missing_user_id")
    case .malformedUserID: return t.t("config_invalid_malformed_user_id")
    case .missingHost: return t.t("config_invalid_missing_host")
    case .malformedHost: return t.t("config_invalid_malformed_host")
    case .missingPort: return t.t("config_invalid_missing_port")
    case .invalidPort: return t.t("config_invalid_port")
    case .malformedEncoding: return t.t("config_invalid_encoding")
    case let .missingParameter(parameter):
        return parameter == "pbk" ? t.t("config_invalid_missing_reality_key") : t.t("config_invalid_missing_parameter", parameter)
    case let .conflictingParameters(parameters): return t.t("config_invalid_conflicting", parameters.joined(separator: ", "))
    }
}

func unsupportedMessage(_ t: Translator, _ feature: UnsupportedFeature) -> String {
    switch feature {
    case let .security(v): return t.t("config_unsupported_security", v)
    case let .transport(v): return t.t("config_unsupported_transport", v)
    case let .flow(v): return t.t("config_unsupported_flow", v)
    case let .encryption(v): return t.t("config_unsupported_encryption", v)
    }
}

func subscriptionMessage(_ t: Translator, _ reason: SubscriptionFailure, _ host: String?) -> String {
    switch reason {
    case .insecure: return t.t("config_subscription_insecure")
    case .unreachable: return t.t("config_subscription_unreachable", host ?? "")
    case .refused: return t.t("config_subscription_refused", host ?? "")
    case .tooLarge: return t.t("config_subscription_too_large", host ?? "")
    case .empty: return t.t("config_subscription_empty")
    }
}

struct AddScreen: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    private enum Step { case method, preview, review, done }
    private enum Sub {
        case loading(host: String)
        case list(token: String, host: String, configs: [ConfigPreview], skipped: Int, truncated: Bool)
        case failed(host: String?, reason: SubscriptionFailure)
    }

    @State private var step = Step.method
    @State private var link = ""
    @State private var name = ""
    @State private var saving = false
    @State private var subscription: Sub?
    @State private var imported = 0
    @State private var token: String?

    private var isSubscription: Bool { SubscriptionLink.parse(link) != nil || SubscriptionLink.isInsecure(link) }
    private var detection: Detection { isSubscription ? .waiting : Detection.of(link) }

    var body: some View {
        let t = model.t
        Group {
            switch step {
            case .done: done(t)
            case .review: review(t)
            case .preview: preview(t)
            case .method: method(t)
            }
        }
        .onDisappear {
            // A held subscription list is let go of when the screen goes away.
            if let token { Task { await model.discardSubscription(token) } }
        }
        .onAppear {
            // A link already on the clipboard is offered, never taken: the field starts empty.
        }
    }

    private func close() { nav.home() }

    private func paste() -> String? {
        guard let text = NSPasteboard.general.string(forType: .string)?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty else {
            nav.show(model.t.t("config_invalid_empty"))
            return nil
        }
        link = text
        return text
    }

    private func advance(_ text: String) async {
        if SubscriptionLink.isInsecure(text) {
            subscription = .failed(host: nil, reason: .insecure)
            step = .preview
            return
        }
        if let sub = SubscriptionLink.parse(text) {
            step = .preview
            subscription = .loading(host: sub.host)
            switch await model.fetchSubscription(text) {
            case let .list(tokenValue, host, configs, skipped, truncated):
                token = tokenValue
                subscription = .list(token: tokenValue, host: host, configs: configs, skipped: skipped, truncated: truncated)
            case let .failed(host, reason):
                subscription = .failed(host: host, reason: reason)
            }
            return
        }
        subscription = nil
        if case let .recognized(preview) = Detection.of(text) { name = preview.suggestedName }
        step = .preview
    }

    private func save() async {
        guard case .recognized = detection, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        saving = true
        let id = await model.createFromLink(link, name: name.trimmingCharacters(in: .whitespaces))
        saving = false
        if id == nil {
            nav.show(model.t.t("config_invalid_malformed"))
            return
        }
        imported = 1
        link = ""
        step = .done
    }

    private func importAll() async {
        guard case let .list(tokenValue, _, _, _, _)? = subscription else { return }
        saving = true
        let ids = await model.importSubscription(tokenValue)
        token = nil
        saving = false
        imported = ids.count
        link = ""
        step = .done
    }

    // -- steps ---------------------------------------------------------------------------------------

    private func method(_ t: Translator) -> some View {
        VScreen(title: t.t("config_method_title"), onBack: close) {
            Text(t.t("config_method_have_link")).vText(19, 650).padding(.horizontal, 4).padding(.top, 6)
            VBody(text: t.t("config_method_reads", Parsers.advertisedFormats))
            HStack(alignment: .bottom, spacing: 8) {
                VTextField(label: t.t("config_method_link_label"), text: $link, mono: true, multiline: true)
                VIconButton(icon: .paste, label: t.t("config_action_paste"), tonal: true, size: 48) { _ = paste() }
            }
            .padding(.top, 12)
            VNote(text: t.t("config_method_example_label")).padding(.top, 8)
            VExample(text: exampleLink).accessibilityLabel(t.t("config_method_example_a11y")).padding(.top, 4)
            VBody(text: t.t("config_method_unsure")).padding(.top, 8)
            VBody(text: t.t("config_method_subscription"))
            VNote(text: t.t("config_method_planned", Parsers.advertisedFormats))
            VButton(title: t.t("config_action_continue")) { Task { await advance(link) } }
                .disabled(link.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty).padding(.top, 18)
                .keyboardShortcut(.defaultAction)
        }
    }

    private func preview(_ t: Translator) -> some View {
        VScreen(title: t.t("config_preview_title"), onBack: { step = .method }) {
            chip(t).padding(.top, 8).padding(.bottom, 14)
            previewBody(t)
            if case .list(_, _, let configs, _, _)? = subscription {
                VButton(title: t.n("config_action_add_all", configs.count)) { Task { await importAll() } }.disabled(saving).padding(.top, 18)
            } else if case .loading? = subscription {
            } else if case .recognized = detection {
                VButton(title: t.t("config_action_continue")) { step = .review }.padding(.top, 18).keyboardShortcut(.defaultAction)
            } else {
                VButton(title: t.t("config_action_paste"), icon: .paste) {
                    if let text = paste() { Task { await advance(text) } }
                }.padding(.top, 18)
                VButton(title: t.t("settings_back"), variant: .text) { step = .method }
            }
        }
    }

    @ViewBuilder private func chip(_ t: Translator) -> some View {
        if let subscription {
            switch subscription {
            case .loading(let host): VChip(label: t.t("config_detection_subscription"), detail: host)
            case .list(_, let host, _, _, _): VChip(label: t.t("config_detection_subscription"), detail: host, tone: .success)
            case .failed(let host, _): VChip(label: t.t("config_detection_invalid"), detail: host, tone: .error)
            }
        } else {
            switch detection {
            case .waiting: VChip(label: t.t("config_detection_waiting"))
            case let .recognized(p):
                VChip(label: p.protocolLabel, detail: [p.securityLabel, p.transportLabel].compactMap { $0 }.joined(separator: " · ").lowercased(), tone: .success)
            case let .unsupported(feature): VChip(label: t.t("config_detection_unsupported"), detail: feature.value, tone: .warning)
            case .invalid: VChip(label: t.t("config_detection_invalid"), tone: .error)
            }
        }
    }

    @ViewBuilder private func previewBody(_ t: Translator) -> some View {
        if let subscription {
            switch subscription {
            case let .loading(host):
                VBody(text: t.t("config_subscription_loading", host))
            case let .failed(host, reason):
                Text(subscriptionMessage(t, reason, host)).vText(14, 400, \.error).padding(.horizontal, 4)
            case let .list(_, _, configs, skipped, truncated):
                Text(t.n("config_subscription_found", configs.count)).vText(18, 650).padding(.horizontal, 4).padding(.bottom, 10)
                VList {
                    ForEach(Array(configs.enumerated()), id: \.offset) { _, config in
                        HStack(spacing: 12) {
                            VMark(text: config.mark)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(config.suggestedName).vText(15, 600).lineLimit(1)
                                Text(config.server).font(Fonts.mono(12)).foregroundStyle(theme.text2).lineLimit(1)
                            }
                            Spacer()
                            Text([config.securityLabel, config.transportLabel].compactMap { $0 }.joined(separator: " · ")).vText(12, 500, \.muted)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 10)
                    }
                }
                if truncated { VNote(text: t.n("config_subscription_truncated", configs.count)) }
                if skipped > 0 { VNote(text: t.n("config_subscription_skipped", skipped)) }
                VNote(text: t.t("config_subscription_once"))
            }
        } else {
            switch detection {
            case .waiting:
                Text(t.t("config_paste_title")).vText(18, 650).padding(.horizontal, 4).padding(.bottom, 8)
                VBody(text: t.t("config_paste_body"))
            case let .recognized(p):
                VCard {
                    HStack(spacing: 12) {
                        VMark(text: p.mark)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(name.isEmpty ? p.suggestedName : name).vText(15, 600)
                            Text(p.server).font(Fonts.mono(12)).foregroundStyle(theme.text2)
                        }
                        Spacer()
                    }
                    .padding(16)
                }
            case let .unsupported(feature): VBody(text: unsupportedMessage(t, feature))
            case let .invalid(reason):
                Text(invalidMessage(t, reason)).vText(14, 400, \.error).padding(.horizontal, 4)
            }
        }
    }

    private func review(_ t: Translator) -> some View {
        VScreen(title: t.t("config_review_title"), onBack: { step = .preview }) {
            VTextField(label: t.t("config_field_name"), text: $name, placeholder: t.t("config_name_placeholder")) { Task { await save() } }
            if case let .recognized(p) = detection {
                VCard {
                    VDetailRow(label: t.t("config_field_protocol"), value: [p.protocolLabel, p.securityLabel].compactMap { $0 }.joined(separator: " · "))
                    VDetailRow(label: t.t("config_field_transport"), value: p.transportLabel)
                    VDetailRow(label: t.t("config_field_server"), value: p.server)
                    if let sni = p.serverName { VDetailRow(label: t.t("config_field_sni"), value: sni) }
                    if let fp = p.fingerprint { VDetailRow(label: t.t("config_field_fingerprint"), value: fp) }
                    if let flow = p.flow { VDetailRow(label: t.t("config_field_flow"), value: flow) }
                    VDetailRow(label: t.t("config_field_name"), value: name.trimmingCharacters(in: .whitespaces).isEmpty ? "—" : name, mono: false)
                }
                .padding(.top, 14)
                if !p.keptParameters.isEmpty {
                    VBanner(title: t.t("config_review_kept_title", p.keptParameters.joined(separator: ", ")), message: t.t("config_review_kept_body"))
                }
            }
            VNote(text: t.t("config_review_secrets_note"))
            VButton(title: t.t("config_action_save")) { Task { await save() } }
                .disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty).padding(.top, 18)
        }
    }

    private func done(_ t: Translator) -> some View {
        VScreen(title: "", showsBack: false) {
            VStack(spacing: 12) {
                VSymbol(icon: .check, size: 40, weight: .bold).foregroundStyle(theme.onSuccessContainer)
                    .frame(width: 96, height: 96).background(Circle().fill(theme.successContainer)).padding(.top, 40)
                Text(imported > 1 ? t.n("config_done_many_title", imported) : t.t("config_done_title", name.isEmpty ? t.t("app_name") : name))
                    .vText(24, 650).multilineTextAlignment(.center)
                Text(t.t("config_done_body")).vText(14, 400, \.text2)
                VButton(title: t.t("config_action_done")) { close() }.padding(.top, 30).keyboardShortcut(.defaultAction)
            }
            .frame(maxWidth: .infinity)
        }
    }
}
