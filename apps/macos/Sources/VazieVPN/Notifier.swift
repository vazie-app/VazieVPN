import AppKit
import UserNotifications
import VazieCore

/// The status notification: while a tunnel is up, one quiet notification with a Disconnect button (`VpnNotifications.kt`).
/// It says the state and nothing else: a notification is read on a locked screen, so it never names a configuration.
@MainActor
final class Notifier: NSObject, UNUserNotificationCenterDelegate {
    static let shared = Notifier()

    private static let identifier = "vazie-tunnel"
    private static let category = "vazie-connected"
    private static let disconnect = "vazie-disconnect"

    var onDisconnect: () -> Void = {}
    private var shown = false
    private var shownKey = ""

    /// Notifications belong to an app bundle; a bare executable (`swift run`) has none to belong to.
    private var available: Bool { Bundle.main.bundleURL.pathExtension == "app" }

    private var center: UNUserNotificationCenter? { available ? UNUserNotificationCenter.current() : nil }

    func status() async -> UNAuthorizationStatus {
        guard let center else { return .denied }
        return await center.notificationSettings().authorizationStatus
    }

    func requestAuthorization() async -> Bool {
        guard let center else { return false }
        center.delegate = self
        return (try? await center.requestAuthorization(options: [.alert])) ?? false
    }

    func openSystemSettings() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension") { NSWorkspace.shared.open(url) }
    }

    func prepare() {
        guard let center else { return }
        center.delegate = self
        let action = UNNotificationAction(identifier: Self.disconnect, title: "", options: [])
        center.setNotificationCategories([UNNotificationCategory(identifier: Self.category, actions: [action], intentIdentifiers: [])])
    }

    /// Shown while connected, if allowed; touched again only when what it says has changed, since each touch
    /// would make the system pop its banner up again.
    func sync(connected: Bool, enabled: Bool, t: Translator) async {
        guard let center else { return }
        if enabled, connected {
            let key = "\(t.language.rawValue)"
            if shown && key == shownKey { return }
            guard await status() == .authorized else { return }
            let action = UNNotificationAction(identifier: Self.disconnect, title: t.t("vpn_notification_disconnect"), options: [])
            center.setNotificationCategories([UNNotificationCategory(identifier: Self.category, actions: [action], intentIdentifiers: [])])
            let content = UNMutableNotificationContent()
            content.title = t.t("vpn_notification_title")
            content.body = t.t("vpn_state_connected")
            content.categoryIdentifier = Self.category
            content.interruptionLevel = .passive
            try? await center.add(UNNotificationRequest(identifier: Self.identifier, content: content, trigger: nil))
            shown = true
            shownKey = key
        } else if shown {
            shown = false
            shownKey = ""
            center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
        }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if response.actionIdentifier == "vazie-disconnect" { await MainActor.run { self.onDisconnect() } }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner]
    }
}
