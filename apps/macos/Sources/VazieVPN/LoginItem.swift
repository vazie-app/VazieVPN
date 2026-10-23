import AppKit
import ServiceManagement

/// Starting with the Mac: the system's own list of login items, which is also where a person turns it off. What the system
/// says is the truth; nothing is kept in the app's own files.
@MainActor
enum LoginItem {
    enum State { case on, off, needsApproval, unavailable }

    static var state: State {
        switch SMAppService.mainApp.status {
        case .enabled: return .on
        case .requiresApproval: return .needsApproval
        case .notRegistered: return .off
        // "Not found" is how a copy the system has not registered yet is reported; registering is what makes it known.
        case .notFound: return .off
        @unknown default: return .unavailable
        }
    }

    /// Turns it on or off. Returns whether the system accepted.
    static func set(_ on: Bool) -> Bool {
        do {
            if on { try SMAppService.mainApp.register() } else { try SMAppService.mainApp.unregister() }
            return true
        } catch {
            NSLog("vazie-login: %@", String(describing: error))
            return false
        }
    }

    static func openSystemSettings() { SMAppService.openSystemSettingsLoginItems() }

    /// Whether this launch is the system starting the app at login: it then waits in the menu bar without opening its window.
    static var launchedAtLogin: Bool {
        let event = NSAppleEventManager.shared().currentAppleEvent
        // 'lgit': keyAELaunchedAsLogInItem (an enum code on the 'oapp' event's property data).
        return event?.eventID == AEEventID(kAEOpenApplication) && event?.paramDescriptor(forKeyword: AEKeyword(keyAEPropData))?.enumCodeValue == 0x6C67_6974
    }
}
