import CryptoKit
import Darwin
import Foundation

// What the Mac's DNS and IPv6 said before the whole-Mac tunnel changed them, kept where a crash, a kill or a reboot cannot
// lose it.
//
// The supervisor (see `tun-run.sh`) restores these itself on a normal stop and when its app dies. This record is for what it
// cannot cover: the supervisor killed without a chance to run, or the Mac restarting with the tunnel up (the DNS and IPv6
// settings are saved by macOS across a reboot; the routes and the firewall rules are not). It is written BEFORE anything is
// changed, atomically, and is authenticated with the same Keychain key as the vault, so a program that rewrites it cannot
// make the next launch set the DNS to a server of its choice.
//
// Nothing is restored blindly. A setting is put back only if it is still what the tunnel set: if the person or another app
// changed it since, it is theirs now and is left alone.

public struct NetworkSnapshot: Codable, Equatable, Sendable {
    public struct Service: Codable, Equatable, Sendable {
        public var name: String
        /// Empty means "automatic" (nothing set by hand).
        public var dns: [String]
        /// `Automatic`, `Off`, `Manual`, `Link-local only`, … as `networksetup -getinfo` says.
        public var ipv6: String
    }

    public static let version = 1
    /// The resolvers the tunnel sets, and so the ones a restore recognises as its own.
    public static let tunnelDNS = ["1.1.1.1", "1.0.0.1"]

    public var version: Int = NetworkSnapshot.version
    public var createdAt: Date
    public var services: [Service]
    /// Set once the supervisor has reported that it applied the changes; until then the record only guards against them.
    public var applied: Bool = false

    public init(createdAt: Date = Date(), services: [Service], applied: Bool = false) {
        self.createdAt = createdAt
        self.services = services
        self.applied = applied
    }
}

public enum SnapshotError: Error, Equatable, Sendable { case unreadable, tampered, invalid, write }

/// The file: the document, then a MAC of it, replaced atomically and readable only by its owner.
public struct NetworkSnapshotStore: Sendable {
    public let url: URL
    private let keys: VaultKeyStore

    public init(url: URL, keys: VaultKeyStore) {
        self.url = url
        self.keys = keys
    }

    private struct Envelope: Codable {
        var body: Data
        var mac: Data
    }

    public func exists() -> Bool { FileManager.default.fileExists(atPath: url.path) }

    /// Written to a new file in the same directory, flushed to the disk, then renamed over the old one: a crash leaves the
    /// old record or the new one, never half of either.
    public func save(_ snapshot: NetworkSnapshot) throws {
        guard let key = try? keys.key() else { throw SnapshotError.write }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        encoder.dateEncodingStrategy = .secondsSince1970
        guard let body = try? encoder.encode(snapshot) else { throw SnapshotError.write }
        let mac = Data(HMAC<SHA256>.authenticationCode(for: body, using: key))
        guard let data = try? JSONEncoder().encode(Envelope(body: body, mac: mac)) else { throw SnapshotError.write }

        let directory = url.deletingLastPathComponent()
        do { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700]) } catch { throw SnapshotError.write }
        let temporary = directory.appendingPathComponent(".snapshot-\(UUID().uuidString).tmp")
        let fd = open(temporary.path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0o600)
        guard fd >= 0 else { throw SnapshotError.write }
        var written = 0
        let ok = data.withUnsafeBytes { raw -> Bool in
            while written < data.count {
                let n = Darwin.write(fd, raw.baseAddress!.advanced(by: written), data.count - written)
                if n <= 0 { return false }
                written += n
            }
            return true
        }
        // F_FULLFSYNC: the drive's own cache too, so the bytes are on the medium before the rename names them.
        let synced = ok && fcntl(fd, F_FULLFSYNC) != -1
        close(fd)
        guard synced, rename(temporary.path, url.path) == 0 else {
            try? FileManager.default.removeItem(at: temporary)
            throw SnapshotError.write
        }
    }

    /// The record, if it is there, whole, authentic and sensible. Anything else is moved aside (`.bad`) so that it is
    /// neither trusted nor lost, and the reason is thrown.
    public func load() throws -> NetworkSnapshot? {
        guard exists() else { return nil }
        // A record that is a link, or a file other users can write to, is not ours.
        var info = stat()
        guard lstat(url.path, &info) == 0, (info.st_mode & S_IFMT) == S_IFREG, info.st_uid == getuid(), (info.st_mode & 0o022) == 0 else {
            quarantine()
            throw SnapshotError.tampered
        }
        guard let data = try? Data(contentsOf: url), data.count < 1 << 20, let envelope = try? JSONDecoder().decode(Envelope.self, from: data) else {
            quarantine()
            throw SnapshotError.unreadable
        }
        guard let key = try? keys.key() else { throw SnapshotError.unreadable }
        guard HMAC<SHA256>.isValidAuthenticationCode(envelope.mac, authenticating: envelope.body, using: key) else {
            quarantine()
            throw SnapshotError.tampered
        }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        guard let snapshot = try? decoder.decode(NetworkSnapshot.self, from: envelope.body), Self.isSane(snapshot) else {
            quarantine()
            throw SnapshotError.invalid
        }
        return snapshot
    }

    public func remove() { try? FileManager.default.removeItem(at: url) }

    private func quarantine() {
        let bad = url.appendingPathExtension("bad")
        try? FileManager.default.removeItem(at: bad)
        try? FileManager.default.moveItem(at: url, to: bad)
    }

    static func isAddress(_ text: String) -> Bool {
        var v4 = in_addr()
        var v6 = in6_addr()
        return inet_pton(AF_INET, text, &v4) == 1 || inet_pton(AF_INET6, text, &v6) == 1
    }

    /// What a restore may be asked to write: a document that passed the MAC is still checked for shape.
    static func isSane(_ snapshot: NetworkSnapshot) -> Bool {
        guard snapshot.version == NetworkSnapshot.version, snapshot.services.count <= 16 else { return false }
        let ipv6 = ["Automatic", "Off", "Manual", "Link-local only"]
        for service in snapshot.services {
            guard !service.name.isEmpty, service.name.utf8.count <= 128, !service.name.contains("\n"), !service.name.hasPrefix("-"),
                service.dns.count <= 8, service.dns.allSatisfy(isAddress), ipv6.contains(service.ipv6)
            else { return false }
        }
        return true
    }
}

/// What the app asks of recovery, so that the model can be tested without a Mac's network.
public protocol NetworkRecovering: Sendable {
    func begin() async -> Bool
    func reconcile(supervisorAlive: Bool) async -> NetworkRecovery.Outcome
    func privilegedCommand() async -> String?
}

/// Reads the Mac's DNS and IPv6, restores them, and tells a leftover from something that is somebody else's.
public struct NetworkRecovery: NetworkRecovering, Sendable {
    public enum Outcome: Equatable, Sendable {
        /// Nothing was recorded, or everything was already as recorded.
        case clean
        /// The supervisor is running: the record is its.
        case inUse
        /// Put back; the record is gone.
        case restored([String])
        /// Something differs and the restore could not be made without privileges; the record is kept.
        case needsPrivileges([String])
        /// The record could not be trusted and was set aside.
        case discarded(SnapshotError)
    }

    private let runner: NetworkSetupRunning
    private let store: NetworkSnapshotStore

    public init(store: NetworkSnapshotStore, runner: NetworkSetupRunning = NetworkSetup()) {
        self.store = store
        self.runner = runner
    }

    private func text(_ arguments: [String]) async -> String? {
        guard let result = await runner.run(arguments), result.status == 0 else { return nil }
        return String(decoding: result.output, as: UTF8.self)
    }

    static func parseDNS(_ output: String) -> [String] {
        if output.contains("aren't any") { return [] }
        return output.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }

    static func parseIPv6(_ info: String) -> String? {
        for line in info.split(separator: "\n") where line.hasPrefix("IPv6:") {
            return line.dropFirst("IPv6:".count).trimmingCharacters(in: .whitespaces)
        }
        return nil
    }

    /// The hardware services as they are now.
    public func capture() async -> NetworkSnapshot? {
        guard let order = await text(["-listnetworkserviceorder"]) else { return nil }
        var services: [NetworkSnapshot.Service] = []
        for name in SystemProxy.parseServices(order) {
            guard let dns = await text(["-getdnsservers", name]), let info = await text(["-getinfo", name]) else { return nil }
            let ipv6 = Self.parseIPv6(info) ?? "Automatic"
            services.append(.init(name: name, dns: Self.parseDNS(dns), ipv6: ["Automatic", "Off", "Manual", "Link-local only"].contains(ipv6) ? ipv6 : "Automatic"))
        }
        guard !services.isEmpty else { return nil }
        return NetworkSnapshot(services: services)
    }

    /// Records the state before the tunnel changes it. If a record is already there (a previous session ended badly) the
    /// OLD one is kept: it holds the person's settings, while the Mac may now be showing the tunnel's.
    @discardableResult
    public func begin() async -> Bool {
        if store.exists() {
            if (try? store.load()) != nil { return true }
        }
        guard var snapshot = await capture() else { return false }
        // Written as "applied" before the tunnel does anything: a crash between the two must not leave changes no record
        // admits to. If the tunnel never changed anything, a restore finds nothing that differs and just forgets it.
        snapshot.applied = true
        return (try? store.save(snapshot)) != nil
    }

    /// The commands that would put back what is still the tunnel's. Empty when nothing is.
    func plan(for snapshot: NetworkSnapshot) async -> [[String]] {
        var commands: [[String]] = []
        for saved in snapshot.services {
            if let dns = await text(["-getdnsservers", saved.name]).map(Self.parseDNS), dns == NetworkSnapshot.tunnelDNS, saved.dns != dns {
                commands.append(["-setdnsservers", saved.name] + (saved.dns.isEmpty ? ["Empty"] : saved.dns))
            }
            if saved.ipv6 == "Automatic", snapshot.applied,
                let info = await text(["-getinfo", saved.name]), Self.parseIPv6(info) == "Off"
            {
                commands.append(["-setv6automatic", saved.name])
            }
        }
        return commands
    }

    /// At launch, and after a tunnel ended: put back what is left of a tunnel, once, and forget the record.
    public func reconcile(supervisorAlive: Bool) async -> Outcome {
        let snapshot: NetworkSnapshot?
        do { snapshot = try store.load() } catch let error as SnapshotError { return .discarded(error) } catch { return .discarded(.unreadable) }
        guard let snapshot else { return .clean }
        if supervisorAlive { return .inUse }

        let commands = await plan(for: snapshot)
        var failed: [[String]] = []
        for command in commands {
            guard let result = await runner.run(command), result.status == 0 else { failed.append(command); continue }
        }
        if failed.isEmpty {
            store.remove()
            return commands.isEmpty ? .clean : .restored(commands.map { $0[0] })
        }
        return .needsPrivileges(failed.map { $0[0] })
    }

    /// The same restore, as one shell command for the administrator prompt: used when the user's own `networksetup` was
    /// refused. Every argument is a value this type validated; none is taken from the record without checking.
    public func privilegedCommand() async -> String? {
        guard let snapshot = try? store.load() else { return nil }
        let commands = await plan(for: snapshot)
        guard !commands.isEmpty else { return nil }
        return commands.map { (["/usr/sbin/networksetup"] + $0).map(AdministratorPrompt.quote).joined(separator: " ") }.joined(separator: "; ")
    }
}
