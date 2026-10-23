import AppKit
import Combine
import SwiftUI
import VazieCore

/// The two objects every window and menu shares.
@MainActor
enum Shared {
    static let model = AppModel.standard()
    static let nav = Navigator()
}

@main
struct VazieVPNApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @StateObject private var model = Shared.model
    @StateObject private var nav = Shared.nav

    init() {
        // `VazieVPN --uninstall [--delete-configurations]`: removes the app and everything it left, with no window.
        if CommandLine.arguments.contains("--uninstall") {
            runCommandLineUninstall()
        }
        Fonts.register()
    }

    var body: some Scene {
        let theme = Theme.of(model.prefs.appearance)
        Window("Vazie VPN", id: "main") {
            RootView()
                .environmentObject(model)
                .environmentObject(nav)
                .environment(\.theme, theme)
                .preferredColorScheme(theme.isDark ? .dark : .light)
                .frame(minWidth: 420, minHeight: 640)
                .background(WindowStyle(color: theme.background))
                .task { await model.load() }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 480, height: 800)
        .windowResizability(.contentMinSize)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button(model.t.t("shortcut_add_config_long")) { nav.replaceAll(.add) }.keyboardShortcut("n")
            }
            CommandGroup(replacing: .appSettings) {
                Button(model.t.t("settings_title") + "…") { nav.replaceAll(.settings) }.keyboardShortcut(",")
            }
            CommandMenu(model.t.t("mac_menu_connection")) {
                Button(model.snapshot.state.isActive ? model.t.t("widget_action_disconnect") : model.t.t("widget_action_connect")) {
                    Task { await toggle() }
                }
                .keyboardShortcut("k", modifiers: [.command, .shift]).disabled(model.profiles.isEmpty)
                Divider()
                ForEach(recentConnections(model.profiles).prefix(5)) { profile in
                    Button(model.t.t("shortcut_connect_to", profile.name)) { Task { await model.connect(profile.id) } }.disabled(!profile.runnable)
                }
            }
        }

        MenuBarExtra {
            MenuBarRoot()
                .environmentObject(model)
                .environmentObject(nav)
                .task { await model.load() }
        } label: {
            Image(nsImage: IconArt.menuBar(dot(for: model.snapshot.state)))
                .accessibilityLabel("\(model.t.t("app_name")) — \(statusWord(model.t, model.snapshot.state, hasProfiles: !model.profiles.isEmpty))")
        }
        .menuBarExtraStyle(.window)
    }

    private func toggle() async {
        if model.snapshot.state.isActive { await model.disconnect() } else { await model.connect() }
    }
}

/// The menu bar's panel, themed from the preferences as they are now, not as they were when the menu was made.
struct MenuBarRoot: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let theme = Theme.of(model.prefs.appearance)
        MenuBarContent()
            .environment(\.theme, theme)
            .preferredColorScheme(theme.isDark ? .dark : .light)
    }
}

/// The command-line uninstaller, for a Mac where the window cannot be reached (the app was half removed by a cleaner).
func runCommandLineUninstall() -> Never {
    // The configurations stay unless the command says otherwise (`--keep-configurations` is still accepted and means the default).
    let keep = !CommandLine.arguments.contains("--delete-configurations")
    let semaphore = DispatchSemaphore(value: 0)
    var report = Uninstaller.Report()
    Task.detached {
        report = await Uninstaller.run(options: Uninstaller.Options(keepConfigurations: keep))
        semaphore.signal()
    }
    semaphore.wait()
    for path in report.removed { print("removed   \(path)") }
    for path in report.kept { print("kept      \(path)") }
    for path in report.failed { print("could not remove \(path)") }
    print(report.proxyRestored ? "network settings: as they were" : "network settings: could not be confirmed (System Settings → Network → Details → Proxies)")
    if !keep { print(report.keychainCleared ? "keychain key: removed" : "keychain key: not removed") }
    Uninstaller.sweepAfterExit(keepConfigurations: keep)
    exit(report.failed.isEmpty ? 0 : 1)
}

/// The screen the navigator is on, over the theme's background.
struct RootView: View {
    @Environment(\.theme) var theme
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var nav: Navigator

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            if model.loaded {
                if !model.prefs.onboardingDone {
                    OnboardingScreen().padding(.top, 18)
                } else {
                    screen(nav.stack.last)
                        .id(nav.stack.last)
                        .transition(.opacity)
                        // Room for the window's three buttons, which float over the content.
                        .padding(.top, 18)
                }
            }
        }
        .animation(.easeInOut(duration: 0.15), value: nav.stack)
        .overlay(alignment: .bottom) { if let toast = nav.toast { VToast(message: toast) } }
        .animation(.easeOut(duration: 0.2), value: nav.toast)
        .onExitCommand { nav.pop() }
    }

    @ViewBuilder private func screen(_ route: Route?) -> some View {
        switch route {
        case nil: HomeScreen()
        case .add: AddScreen()
        case .details: DetailsScreen()
        case .settings: SettingsScreen()
        case .appearance: AppearanceScreen()
        case .split: SplitScreen()
        case .mode: ModeScreen()
        case .engine: EngineScreen()
        case .guides: GuidesScreen()
        case let .guide(id): GuideDetailScreen(id: id)
        case .faq: FaqScreen()
        case .about: AboutScreen()
        case .privacy: PrivacyScreen()
        case let .config(id): ConfigDetailsScreen(id: id)
        }
    }
}

/// A title bar that is the window's own colour, with no title: the interface draws its own.
struct WindowStyle: NSViewRepresentable {
    let color: Color

    func makeNSView(context: Context) -> NSView { NSView() }

    func updateNSView(_ view: NSView, context: Context) {
        DispatchQueue.main.async {
            guard let window = view.window else { return }
            // The content runs up under the title bar and the window's three buttons float over it, as in Notes or Music.
            window.styleMask.insert(.fullSizeContentView)
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.titlebarSeparatorStyle = .none
            window.backgroundColor = NSColor(color)
            window.isMovableByWindowBackground = true
        }
    }
}

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var observers = Set<AnyCancellable>()

    func applicationDidFinishLaunching(_ notification: Notification) {
        let model = Shared.model
        Notifier.shared.prepare()
        Notifier.shared.onDisconnect = { Task { await model.disconnect() } }
        // The Dock icon is the chosen one, with the state's dot; the notification follows the state.
        Publishers.CombineLatest3(model.$prefs, model.$snapshot, model.$language)
            .receive(on: DispatchQueue.main)
            .sink { prefs, snapshot, _ in
                let plate = Icons.plate(prefs.appIconPlate, for: prefs.appIcon)
                NSApp.applicationIconImage = IconArt.icon(prefs.appIcon, plate: plate, size: 512, dot: dot(for: snapshot.state), margin: 0.09)
                Task { await Notifier.shared.sync(connected: snapshot.state.isConnected, enabled: prefs.notificationsEnabled, t: model.t) }
            }
            .store(in: &observers)
        Task { await model.load() }
        watchOwnBundle()
        // Started by the system at login: only the menu bar, no window in the way.
        if LoginItem.launchedAtLogin {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { NSApp.windows.filter { $0.title == "Vazie VPN" || $0.isVisible }.forEach { $0.close() } }
        }
    }

    /// A cleaner that moves the bundle to the Trash while the app runs leaves the process (and the menu bar icon, and a
    /// proxy setting) behind. The app notices its own bundle going and leaves with the network as it found it.
    private var bundleWatch: Timer?

    private func watchOwnBundle() {
        guard Uninstaller.runningBundle() != nil, Bundle.main.bundleURL.pathExtension == "app" else { return }
        var misses = 0
        bundleWatch = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak self] _ in
            // A deleted program has no path any more (the kernel answers nothing); a trashed one has a path in the Trash.
            let now = Uninstaller.runningBundle()
            misses = (now == nil || Uninstaller.bundleIsGone(now)) ? misses + 1 : 0
            guard misses >= 2 else { return }
            self?.bundleWatch?.invalidate()
            Task { @MainActor in
                await Shared.model.shutdown()
                Uninstaller.sweepAfterExit()
                // Leaves at once: there is nothing to save for a program that is no longer there, and a normal quit
                // would write its window state into the files a cleaner has just removed.
                exit(0)
            }
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }

    /// Quitting puts the Mac's network settings back before the app goes: the tunnel ends first.
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        Task { @MainActor in
            await Shared.model.shutdown()
            NSApp.reply(toApplicationShouldTerminate: true)
        }
        return .terminateLater
    }

    /// Your recent configurations under the Dock icon (Android's launcher shortcuts).
    func applicationDockMenu(_ sender: NSApplication) -> NSMenu? {
        let model = Shared.model
        let t = model.t
        let menu = NSMenu()
        if !model.profiles.isEmpty {
            let active = model.snapshot.state.isActive
            menu.addItem(DockItem(active ? t.t("widget_action_disconnect") : t.t("widget_action_connect")) {
                if active { await model.disconnect() } else { await model.connect() }
            })
        }
        let recent = recentConnections(model.profiles)
            .compactMap { profile in Format.safeMenuLabel(profile.name).map { (profile, $0) } }
            .prefix(3)
        for (profile, label) in recent where profile.runnable {
            menu.addItem(DockItem(t.t("shortcut_connect_to", label)) { await model.connect(profile.id) })
        }
        // The one action that is useful before there is anything to connect to.
        if recent.count < 3 {
            menu.addItem(DockItem(t.t("shortcut_add_config_long")) {
                Shared.nav.replaceAll(.add)
                NSApp.activate(ignoringOtherApps: true)
            })
        }
        return menu
    }
}

private final class DockItem: NSMenuItem {
    private let work: () async -> Void

    init(_ title: String, _ work: @escaping () async -> Void) {
        self.work = work
        super.init(title: title, action: #selector(run), keyEquivalent: "")
        target = self
    }

    required init(coder: NSCoder) { fatalError() }

    @objc private func run() { Task { await work() } }
}
