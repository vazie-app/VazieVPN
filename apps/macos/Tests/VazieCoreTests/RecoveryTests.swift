import CryptoKit
import XCTest

@testable import VazieCore

/// A network that remembers what it was told, for the restore logic: DNS and IPv6 per service, the commands it was given, and
/// the ones it refuses.
final class ScriptedNetwork: NetworkSetupRunning, @unchecked Sendable {
    private let lock = NSLock()
    var dns: [String: [String]] = ["Wi-Fi": ["9.9.9.9", "8.8.8.8"], "Ethernet": []]
    var ipv6: [String: String] = ["Wi-Fi": "Automatic", "Ethernet": "Automatic"]
    var commands: [[String]] = []
    var refuse: Set<String> = []
    var listing = "(1) Wi-Fi\n(Hardware Port: Wi-Fi, Device: en1)\n\n(2) Ethernet\n(Hardware Port: Ethernet, Device: en0)\n\n(3) Tailscale\n(Hardware Port: Tailscale, Device: utun3)\n"

    func run(_ arguments: [String]) async -> ProcessRunner.Result? {
        lock.lock(); defer { lock.unlock() }
        func ok(_ text: String = "") -> ProcessRunner.Result { .init(status: 0, output: Data(text.utf8), error: Data()) }
        let failure = ProcessRunner.Result(status: 1, output: Data(), error: Data())
        switch arguments[0] {
        case "-listnetworkserviceorder": return ok(listing)
        case "-getdnsservers":
            let list = dns[arguments[1]] ?? []
            return ok(list.isEmpty ? "There aren't any DNS Servers set on \(arguments[1]).\n" : list.joined(separator: "\n") + "\n")
        case "-getinfo": return ok("DHCP Configuration\nIP address: 10.0.0.2\nIPv6: \(ipv6[arguments[1]] ?? "Automatic")\n")
        case "-setdnsservers":
            commands.append(arguments)
            if refuse.contains("-setdnsservers") { return failure }
            dns[arguments[1]] = arguments[2] == "Empty" ? [] : Array(arguments.dropFirst(2))
            return ok()
        case "-setv6automatic", "-setv6off":
            commands.append(arguments)
            if refuse.contains(arguments[0]) { return failure }
            ipv6[arguments[1]] = arguments[0] == "-setv6off" ? "Off" : "Automatic"
            return ok()
        default: return ok()
        }
    }
}

final class RecoveryTests: XCTestCase {
    var directory: URL!
    var network: ScriptedNetwork!
    var keys: MemoryKeyStore!
    var store: NetworkSnapshotStore!
    var recovery: NetworkRecovery!

    override func setUp() {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("vz-rec-\(UUID().uuidString)")
        network = ScriptedNetwork()
        keys = MemoryKeyStore()
        store = NetworkSnapshotStore(url: directory.appendingPathComponent("mac/network-snapshot.json"), keys: keys)
        recovery = NetworkRecovery(store: store, runner: network)
    }

    override func tearDown() { try? FileManager.default.removeItem(at: directory) }

    /// What the supervisor does to the Mac, as the tunnel does it.
    private func tunnelApplies() {
        network.dns["Wi-Fi"] = NetworkSnapshot.tunnelDNS
        network.ipv6["Wi-Fi"] = "Off"
    }

    // -- the record ----------------------------------------------------------------------------------------------------

    func testTheRecordIsWrittenBeforeAnythingChangesAndHoldsTheOriginalSettings() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        let snapshot = try XCTUnwrap(try store.load())
        XCTAssertEqual(snapshot.services.map(\.name), ["Wi-Fi", "Ethernet"], "hardware services only; a VPN's own is not touched")
        XCTAssertEqual(snapshot.services[0].dns, ["9.9.9.9", "8.8.8.8"])
        XCTAssertEqual(snapshot.services[1].dns, [], "automatic is kept as automatic")
        XCTAssertEqual(snapshot.services[0].ipv6, "Automatic")
        XCTAssertTrue(snapshot.applied)
        let attributes = try FileManager.default.attributesOfItem(atPath: store.url.path)
        XCTAssertEqual((attributes[.posixPermissions] as? NSNumber)?.intValue, 0o600)
        // No temporary file is left next to it.
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: store.url.deletingLastPathComponent().path), ["network-snapshot.json"])
    }

    func testARecordAlreadyThereIsKeptNotOverwrittenWithTheTunnelsOwnValues() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        // The previous session ended badly: the Mac now shows the tunnel's DNS. A new begin must not call that "original".
        tunnelApplies()
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        XCTAssertEqual(try store.load()?.services[0].dns, ["9.9.9.9", "8.8.8.8"])
    }

    func testARecordThatWasChangedOnDiskIsNotTrustedAndIsSetAside() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        var text = try String(contentsOf: store.url, encoding: .utf8)
        // Flip a byte of the sealed body: the MAC no longer matches.
        let envelope = try JSONSerialization.jsonObject(with: Data(text.utf8)) as! [String: Any]
        var body = Data(base64Encoded: envelope["body"] as! String)!
        body[body.startIndex + 5] ^= 0x01
        text = String(decoding: try JSONSerialization.data(withJSONObject: ["body": body.base64EncodedString(), "mac": envelope["mac"]!]), as: UTF8.self)
        try text.write(to: store.url, atomically: true, encoding: .utf8)
        XCTAssertThrowsError(try store.load()) { XCTAssertEqual($0 as? SnapshotError, .tampered) }
        XCTAssertFalse(store.exists())
        XCTAssertTrue(FileManager.default.fileExists(atPath: store.url.appendingPathExtension("bad").path), "kept for a look, not trusted")
        let outcome = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(outcome, .clean, "nothing left to act on")
        XCTAssertEqual(network.commands.count, 0)
    }

    func testARecordSealedWithAnotherKeyIsRefused() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        let stranger = NetworkSnapshotStore(url: store.url, keys: MemoryKeyStore())
        XCTAssertThrowsError(try stranger.load()) { XCTAssertEqual($0 as? SnapshotError, .tampered) }
    }

    func testATruncatedOrGarbageRecordIsUnreadableNotAnAction() async throws {
        try FileManager.default.createDirectory(at: store.url.deletingLastPathComponent(), withIntermediateDirectories: true)
        for junk in ["", "{", "{\"body\":\"AA==\"}", String(repeating: "x", count: 100)] {
            try junk.write(to: store.url, atomically: true, encoding: .utf8)
            XCTAssertThrowsError(try store.load(), junk) { XCTAssertEqual($0 as? SnapshotError, .unreadable) }
        }
        let outcome = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(outcome, .clean)
        XCTAssertTrue(network.commands.isEmpty)
    }

    func testARecordThatIsASymlinkOrWritableByOthersIsNotOurs() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        // Group-writable.
        chmod(store.url.path, 0o664)
        XCTAssertThrowsError(try store.load()) { XCTAssertEqual($0 as? SnapshotError, .tampered) }
        // A link to a record that would otherwise be valid.
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        let real = store.url.deletingLastPathComponent().appendingPathComponent("real.json")
        try FileManager.default.moveItem(at: store.url, to: real)
        try FileManager.default.createSymbolicLink(at: store.url, withDestinationURL: real)
        XCTAssertThrowsError(try store.load()) { XCTAssertEqual($0 as? SnapshotError, .tampered) }
    }

    func testAnAuthenticRecordWithValuesNoRestoreMayWriteIsRefused() throws {
        for (name, dns, v6) in [("-setdnsservers", ["1.1.1.1"], "Automatic"), ("Wi-Fi", ["1.1.1.1; reboot"], "Automatic"), ("Wi-Fi", ["1.1.1.1"], "Weird"), ("Wi-Fi\nEthernet", [], "Off")] {
            let evil = NetworkSnapshot(services: [.init(name: name, dns: dns, ipv6: v6)], applied: true)
            try store.save(evil)
            XCTAssertThrowsError(try store.load(), "\(name) \(dns) \(v6)") { XCTAssertEqual($0 as? SnapshotError, .invalid) }
        }
    }

    // -- putting it back -----------------------------------------------------------------------------------------------

    func testWhatTheTunnelLeftIsPutBackAndTheRecordIsForgotten() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        // The supervisor is gone (killed, or the Mac restarted: the settings stay, the process does not).
        let outcome = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(outcome, .restored(["-setdnsservers", "-setv6automatic"]))
        XCTAssertEqual(network.dns["Wi-Fi"], ["9.9.9.9", "8.8.8.8"])
        XCTAssertEqual(network.ipv6["Wi-Fi"], "Automatic")
        XCTAssertFalse(store.exists())
    }

    func testDnsThatWasAutomaticIsPutBackAsAutomatic() async throws {
        network.dns["Wi-Fi"] = []
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        _ = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(network.commands.first, ["-setdnsservers", "Wi-Fi", "Empty"])
        XCTAssertEqual(network.dns["Wi-Fi"], [])
    }

    func testWhatSomebodyElseChangedSinceIsLeftAlone() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        // The person (or another app) has since chosen their own DNS and turned IPv6 on again.
        network.dns["Wi-Fi"] = ["4.4.4.4"]
        network.ipv6["Wi-Fi"] = "Automatic"
        let outcome = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(outcome, .clean)
        XCTAssertTrue(network.commands.isEmpty, "\(network.commands)")
        XCTAssertEqual(network.dns["Wi-Fi"], ["4.4.4.4"])
        XCTAssertFalse(store.exists())
    }

    func testIPv6TheUserHadOffIsNotTurnedOn() async throws {
        network.ipv6["Wi-Fi"] = "Off"
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        _ = await recovery.reconcile(supervisorAlive: false)
        XCTAssertFalse(network.commands.contains { $0[0] == "-setv6automatic" })
        XCTAssertEqual(network.ipv6["Wi-Fi"], "Off")
    }

    func testAliveSupervisorMeansTheRecordIsItsAndNothingIsTouched() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        let outcome = await recovery.reconcile(supervisorAlive: true)
        XCTAssertEqual(outcome, .inUse)
        XCTAssertTrue(network.commands.isEmpty)
        XCTAssertTrue(store.exists())
    }

    func testARestoreThatTheSystemRefusesKeepsTheRecordAndCanBeRepeated() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        tunnelApplies()
        network.refuse = ["-setdnsservers"]
        let first = await recovery.reconcile(supervisorAlive: false)
        XCTAssertEqual(first, .needsPrivileges(["-setdnsservers"]))
        XCTAssertTrue(store.exists(), "the way back is not thrown away")
        // The same restore, as the one command the administrator prompt would run, quoted.
        let maybe = await recovery.privilegedCommand()
        let command = try XCTUnwrap(maybe)
        XCTAssertTrue(command.contains("'/usr/sbin/networksetup' '-setdnsservers' 'Wi-Fi' '9.9.9.9' '8.8.8.8'"), command)
        // Later it works, and a third run has nothing left to do.
        network.refuse = []
        do { let v = await recovery.reconcile(supervisorAlive: false); XCTAssertEqual(v, .restored(["-setdnsservers"])) }
        do { let v = await recovery.reconcile(supervisorAlive: false); XCTAssertEqual(v, .clean) }
        let count = network.commands.count
        do { let v = await recovery.reconcile(supervisorAlive: false); XCTAssertEqual(v, .clean) }
        XCTAssertEqual(network.commands.count, count, "a cleanup that has been done is not done again")
    }

    func testNothingRecordedMeansNothingToDo() async {
        do { let v = await recovery.reconcile(supervisorAlive: false); XCTAssertEqual(v, .clean) }
        XCTAssertTrue(network.commands.isEmpty)
    }

    func testATunnelThatNeverChangedAnythingJustDropsTheRecord() async throws {
        do { let v = await recovery.begin(); XCTAssertTrue(v) }
        do { let v = await recovery.reconcile(supervisorAlive: false); XCTAssertEqual(v, .clean) }
        XCTAssertTrue(network.commands.isEmpty)
        XCTAssertFalse(store.exists())
    }

    func testARecordThatCannotBeTakenMeansNoRecordIsWritten() async {
        network.listing = "garbage"
        do { let v = await recovery.begin(); XCTAssertFalse(v) }
        XCTAssertFalse(store.exists())
    }

    // -- what a dead supervisor leaves ---------------------------------------------------------------------------------

    func testALeftoverSupervisorIsRecognisedFromItsFiles() throws {
        let run = directory.appendingPathComponent("run")
        try FileManager.default.createDirectory(at: run, withIntermediateDirectories: true)
        let paths = TunPaths(run: run)
        XCTAssertEqual(TunLeftovers.inspect(paths: paths), .init(supervisorAlive: false, unfinished: false, wasBlocking: false))
        try "running 0123456789abcdef\n".write(to: paths.status, atomically: true, encoding: .utf8)
        try "killswitch=on\n".write(to: paths.info, atomically: true, encoding: .utf8)
        try "99999999\n".write(to: paths.pid, atomically: true, encoding: .utf8)
        XCTAssertEqual(TunLeftovers.inspect(paths: paths), .init(supervisorAlive: false, unfinished: true, wasBlocking: true))
        // A supervisor that finished says so.
        try "stopped\n".write(to: paths.status, atomically: true, encoding: .utf8)
        XCTAssertEqual(TunLeftovers.inspect(paths: paths).unfinished, false)
        // One that is alive (this very process stands in) is not a leftover.
        try "\(getpid())\n".write(to: paths.pid, atomically: true, encoding: .utf8)
        try "running 0123456789abcdef\n".write(to: paths.status, atomically: true, encoding: .utf8)
        XCTAssertEqual(TunLeftovers.inspect(paths: paths), .init(supervisorAlive: true, unfinished: false, wasBlocking: false))
    }
}
