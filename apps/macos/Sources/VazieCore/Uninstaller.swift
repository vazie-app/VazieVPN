import AppKit
import Foundation
import Security
import ServiceManagement

/// Removes Vazie VPN from a Mac completely: the app, what it keeps, what it left in the system, and nothing that is
/// not its own. A cleaner that only moves the bundle to the Trash leaves a running process, a proxy setting pointing at
/// a dead port, the encrypted configurations and their key; this puts every one of them right.
public enum Uninstaller {
    public static let bundleIdentifier = "app.vazie.vpn.mac"
    public static let keychainService = "app.vazie.vpn.mac"

    public struct Options: Sendable {
        /// Leave the encrypted configurations and their key (a reinstall finds them). The default: a person's server
        /// configurations are not something an uninstaller should lose on its own; deleting them takes an explicit choice.
        public var keepConfigurations = true
        /// Move the app bundle to the Trash (when it is somewhere that can be written to).
        public var trashApp = true
        /// Ask other running copies of the app to quit first (they put the network settings back as they go).
        public var quitRunningCopies = true

        public init(keepConfigurations: Bool = true, trashApp: Bool = true, quitRunningCopies: Bool = true) {
            self.keepConfigurations = keepConfigurations
            self.trashApp = trashApp
            self.quitRunningCopies = quitRunningCopies
        }
    }

    public struct Report: Equatable, Sendable {
        public var removed: [String] = []
        public var failed: [String] = []
        /// What was deliberately left, with the reason.
        public var kept: [String] = []
        public var proxyRestored = false
        public var keychainCleared = false
        public var appTrashed = false

        public init() {}
    }

    /// Everything the app puts under `~/Library`, in the order it is removed. `engineIsShared` is whether the browser
    /// extension's helper lives beside the engine (`bin/`), which then belongs to it as well.
    static func targets(library: URL, keepConfigurations: Bool) -> (remove: [URL], kept: [(URL, String)]) {
        let support = library.appendingPathComponent("Application Support/Vazie VPN", isDirectory: true)
        let id = bundleIdentifier
        var remove: [URL] = []
        var kept: [(URL, String)] = []

        let own = support.appendingPathComponent("mac", isDirectory: true)
        if keepConfigurations {
            // Everything of the app's but the vault.
            for name in ["prefs.json", "cache-links.json", "cache-faq.json", "system-proxy.json", "network-snapshot.json", "network-snapshot.json.bad", "engine.pid"] { remove.append(own.appendingPathComponent(name)) }
            kept.append((own.appendingPathComponent("profiles.vault"), "configurations"))
        } else {
            remove.append(own)
        }

        // The engine is shared with the extension's helper: it goes only when the helper is not there.
        let engine = support.appendingPathComponent("engine", isDirectory: true)
        if FileManager.default.fileExists(atPath: support.appendingPathComponent("bin").path) {
            if FileManager.default.fileExists(atPath: engine.path) { kept.append((engine, "used by the browser extension's helper")) }
        } else {
            remove.append(engine)
        }

        remove += [
            library.appendingPathComponent("Preferences/\(id).plist"),
            library.appendingPathComponent("Caches/\(id)", isDirectory: true),
            library.appendingPathComponent("HTTPStorages/\(id)", isDirectory: true),
            library.appendingPathComponent("HTTPStorages/\(id).binarycookies"),
            library.appendingPathComponent("Saved Application State/\(id).savedState", isDirectory: true),
            library.appendingPathComponent("WebKit/\(id)", isDirectory: true),
            library.appendingPathComponent("Containers/\(id)", isDirectory: true),
            library.appendingPathComponent("Logs/Vazie VPN", isDirectory: true),
        ]
        return (remove, kept)
    }

    /// What `run` would remove, for the dialog that asks first.
    public static func preview(library: URL = defaultLibrary, keepConfigurations: Bool) -> [URL] {
        targets(library: library, keepConfigurations: keepConfigurations).remove.filter { FileManager.default.fileExists(atPath: $0.path) }
    }

    public static var defaultLibrary: URL { FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Library", isDirectory: true) }

    /// Another running copy of the app asked to quit, and made to if it will not.
    static func quitOthers() async {
        let me = ProcessInfo.processInfo.processIdentifier
        let others = NSRunningApplication.runningApplications(withBundleIdentifier: bundleIdentifier).filter { $0.processIdentifier != me }
        for app in others { app.terminate() }
        for _ in 0..<40 where others.contains(where: { !$0.isTerminated }) { try? await Task.sleep(nanoseconds: 200_000_000) }
        for app in others where !app.isTerminated { app.forceTerminate() }
    }

    /// Deletes the encrypted store's key. A missing key is not a failure.
    static func clearKeychain() -> Bool {
        let status = SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: keychainService] as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    /// Whether an app bundle can be taken to the Trash from where it is: not a disk image and not a build folder.
    static func canTrash(_ bundle: URL) -> Bool {
        let path = bundle.path
        return !path.hasPrefix("/Volumes/") && !path.contains("/.build/") && !path.contains("/.Trash/") && bundle.pathExtension == "app"
    }

    public static func run(
        app: URL? = Bundle.main.bundleURL, options: Options = Options(), library: URL = defaultLibrary,
        proxy: SystemProxyControlling = SystemProxy(), clearsKeychain: Bool = true
    ) async -> Report {
        var report = Report()
        let fm = FileManager.default

        // 1. Nothing of ours keeps running, and the Mac's network settings are as they were.
        if options.quitRunningCopies { await quitOthers() }
        await proxy.restore()
        report.proxyRestored = !proxy.needsRecovery()
        OrphanedEngine.clean(at: library.appendingPathComponent("Application Support/Vazie VPN/mac/engine.pid"))

        // 2. What it keeps.
        let plan = targets(library: library, keepConfigurations: options.keepConfigurations)
        for url in plan.remove where fm.fileExists(atPath: url.path) {
            do {
                try fm.removeItem(at: url)
                report.removed.append(url.path)
            } catch {
                report.failed.append(url.path)
            }
        }
        for (url, why) in plan.kept where fm.fileExists(atPath: url.path) { report.kept.append("\(url.path) (\(why))") }
        // The folder itself, if nothing of the helper's is in it.
        let support = library.appendingPathComponent("Application Support/Vazie VPN", isDirectory: true)
        if let rest = try? fm.contentsOfDirectory(atPath: support.path), rest.allSatisfy({ $0 == ".DS_Store" }) { try? fm.removeItem(at: support) }

        // 3. The key to the configurations.
        if !options.keepConfigurations {
            if clearsKeychain { report.keychainCleared = clearKeychain() }
        }

        // The login item, so that the Mac does not go looking for an app that is gone.
        if app != nil { try? await SMAppService.mainApp.unregister() }

        // 4. The app, and the system's memory of it.
        if let app, options.trashApp, canTrash(app) {
            unregister(app)
            do {
                try fm.trashItem(at: app, resultingItemURL: nil)
                report.appTrashed = true
                report.removed.append(app.path)
            } catch {
                report.failed.append(app.path)
            }
        }
        return report
    }

    /// Takes the app out of Launch Services, so that Launchpad, Spotlight and "Open With" stop offering it.
    static func unregister(_ app: URL) {
        let tool = "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
        let process = Process()
        process.executableURL = URL(fileURLWithPath: tool)
        process.arguments = ["-u", app.path]
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try? process.run()
        process.waitUntilExit()
    }

    /// What AppKit writes as an app quits (its window position, its saved state) would bring the files back a moment
    /// after they were removed; this runs after the app has gone and removes them once more.
    public static func sweepAfterExit(library: URL = defaultLibrary, keepConfigurations: Bool = true) {
        let paths = targets(library: library, keepConfigurations: keepConfigurations).remove
            .filter { $0.path.contains("/Preferences/") || $0.path.contains("/Saved Application State/") || $0.path.contains("/Caches/") || $0.path.contains("/HTTPStorages/") }
            .map(\.path)
        let script = "sleep 2; rm -rf -- " + paths.map { "'" + $0.replacingOccurrences(of: "'", with: "'\\''") + "'" }.joined(separator: " ")
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/sh")
        process.arguments = ["-c", script]
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        process.standardInput = FileHandle.nullDevice
        try? process.run()
    }

    /// Where the running program really is now. `Bundle.main` keeps the path it was started from, and follows the bundle
    /// when it moves; the kernel's answer for the process is the one that says "the Trash".
    public static func runningBundle() -> URL? {
        var buffer = [CChar](repeating: 0, count: 4 * Int(MAXPATHLEN))
        guard proc_pidpath(getpid(), &buffer, UInt32(buffer.count)) > 0 else { return nil }
        var url = URL(fileURLWithPath: String(cString: buffer))
        while url.path != "/" && url.pathExtension != "app" { url.deleteLastPathComponent() }
        return url.pathExtension == "app" ? url : nil
    }

    /// A running app whose bundle has been deleted or taken to the Trash from under it: the sign that a cleaner removed it.
    public static func bundleIsGone(_ bundle: URL? = runningBundle()) -> Bool {
        guard let bundle, bundle.pathExtension == "app" else { return false }
        return !FileManager.default.fileExists(atPath: bundle.path) || bundle.path.contains("/.Trash/")
    }
}
