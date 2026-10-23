import XCTest

@testable import VazieCore

final class UninstallerTests: XCTestCase {
    private var library: URL!
    private let fm = FileManager.default

    override func setUp() {
        library = fm.temporaryDirectory.appendingPathComponent("vazie-lib-\(UUID().uuidString)")
    }

    override func tearDown() { try? fm.removeItem(at: library) }

    private func touch(_ path: String, directory: Bool = false) {
        let url = library.appendingPathComponent(path)
        if directory {
            try? fm.createDirectory(at: url, withIntermediateDirectories: true)
        } else {
            try? fm.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            fm.createFile(atPath: url.path, contents: Data("x".utf8))
        }
    }

    private func exists(_ path: String) -> Bool { fm.fileExists(atPath: library.appendingPathComponent(path).path) }

    private func populate() {
        touch("Application Support/Vazie VPN/mac/profiles.vault")
        touch("Application Support/Vazie VPN/mac/prefs.json")
        touch("Application Support/Vazie VPN/mac/system-proxy.json")
        touch("Application Support/Vazie VPN/engine/v26.7.28/xray")
        touch("Preferences/app.vazie.vpn.mac.plist")
        touch("Caches/app.vazie.vpn.mac/a", directory: false)
        touch("Saved Application State/app.vazie.vpn.mac.savedState/windows.plist")
        touch("HTTPStorages/app.vazie.vpn.mac/x")
        // Somebody else's, which must stay.
        touch("Preferences/com.example.other.plist")
        touch("Application Support/Other App/data")
    }

    private func run(_ options: Uninstaller.Options) async -> Uninstaller.Report {
        await Uninstaller.run(app: nil, options: Uninstaller.Options(keepConfigurations: options.keepConfigurations, trashApp: false, quitRunningCopies: false), library: library, proxy: FakeProxy(), clearsKeychain: false)
    }

    func testEverythingOfTheAppsIsRemovedAndNothingElse() async {
        populate()
        // Deleting the configurations too is an explicit choice.
        let report = await run(Uninstaller.Options(keepConfigurations: false))
        XCTAssertEqual(report.failed, [])
        for path in ["Application Support/Vazie VPN", "Preferences/app.vazie.vpn.mac.plist", "Caches/app.vazie.vpn.mac", "Saved Application State/app.vazie.vpn.mac.savedState", "HTTPStorages/app.vazie.vpn.mac"] {
            XCTAssertFalse(exists(path), path)
        }
        XCTAssertTrue(exists("Preferences/com.example.other.plist"))
        XCTAssertTrue(exists("Application Support/Other App/data"))
        XCTAssertTrue(report.proxyRestored)
    }

    func testByDefaultTheConfigurationsAreKeptAndOnlyAnExplicitChoiceDeletesThem() async {
        XCTAssertTrue(Uninstaller.Options().keepConfigurations, "an uninstaller does not lose a person's servers on its own")
        populate()
        let report = await run(Uninstaller.Options())
        XCTAssertTrue(exists("Application Support/Vazie VPN/mac/profiles.vault"), "the vault survives")
        XCTAssertFalse(exists("Application Support/Vazie VPN/mac/prefs.json"))
        XCTAssertFalse(exists("Preferences/app.vazie.vpn.mac.plist"))
        XCTAssertFalse(report.keychainCleared, "and so does the key that opens it")
        XCTAssertEqual(report.failed, [])
    }

    func testTheConfigurationsCanBeKeptForAReinstall() async {
        populate()
        let report = await run(Uninstaller.Options(keepConfigurations: true))
        XCTAssertTrue(exists("Application Support/Vazie VPN/mac/profiles.vault"))
        XCTAssertFalse(exists("Application Support/Vazie VPN/mac/prefs.json"))
        XCTAssertFalse(exists("Application Support/Vazie VPN/mac/system-proxy.json"))
        XCTAssertFalse(exists("Preferences/app.vazie.vpn.mac.plist"))
        XCTAssertTrue(report.kept.contains { $0.contains("profiles.vault") })
    }

    func testTheEngineBelongsToTheExtensionsHelperWhenItIsInstalled() async {
        populate()
        touch("Application Support/Vazie VPN/bin/vazie-vpn-host")
        let report = await run(Uninstaller.Options(keepConfigurations: false))
        XCTAssertTrue(exists("Application Support/Vazie VPN/engine/v26.7.28/xray"))
        XCTAssertTrue(exists("Application Support/Vazie VPN/bin/vazie-vpn-host"))
        XCTAssertFalse(exists("Application Support/Vazie VPN/mac"))
        XCTAssertTrue(report.kept.contains { $0.contains("helper") })
    }

    func testTheNetworkSettingsArePutBackBeforeAnythingIsDeleted() async {
        populate()
        let proxy = FakeProxy()
        proxy.recovery = true
        _ = await Uninstaller.run(app: nil, options: Uninstaller.Options(trashApp: false, quitRunningCopies: false), library: library, proxy: proxy, clearsKeychain: false)
        XCTAssertEqual(proxy.restored, 1)
    }

    func testAnAppInsideADiskImageOrABuildFolderIsNotTrashed() {
        XCTAssertFalse(Uninstaller.canTrash(URL(fileURLWithPath: "/Volumes/Vazie VPN/Vazie VPN.app")))
        XCTAssertFalse(Uninstaller.canTrash(URL(fileURLWithPath: "/x/.build/debug/Vazie VPN.app")))
        XCTAssertTrue(Uninstaller.canTrash(URL(fileURLWithPath: "/Applications/Vazie VPN.app")))
        XCTAssertFalse(Uninstaller.bundleIsGone(URL(fileURLWithPath: "/usr/bin/true")))
        XCTAssertFalse(Uninstaller.bundleIsGone(nil))
        XCTAssertTrue(Uninstaller.bundleIsGone(URL(fileURLWithPath: "/definitely/not/here/Vazie VPN.app")))
        XCTAssertTrue(Uninstaller.bundleIsGone(URL(fileURLWithPath: "/Users/x/.Trash/Vazie VPN.app")))
    }
}
