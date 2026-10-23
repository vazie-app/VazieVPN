import CryptoKit
import Foundation
import Security

/// Where the vault's key lives. Android keeps the same arrangement with a Keystore key and a file; here the key is
/// a Keychain item that never leaves this Mac, and the file holds only what the key opens.
public protocol VaultKeyStore: Sendable {
    func key() throws -> SymmetricKey
}

public enum VaultError: Error { case keychain(OSStatus), unreadable }

public struct KeychainKeyStore: VaultKeyStore {
    private let service: String
    private let account = "vault-key"

    public init(service: String = "app.vazie.vpn.mac") { self.service = service }

    public func key() throws -> SymmetricKey {
        let match: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        var read = match
        read[kSecReturnData as String] = true
        read[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(read as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data, data.count == 32 { return SymmetricKey(data: data) }
        if status != errSecItemNotFound && status != errSecSuccess { throw VaultError.keychain(status) }

        let fresh = SymmetricKey(size: .bits256)
        var add = match
        add[kSecValueData as String] = fresh.withUnsafeBytes { Data($0) }
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        add[kSecAttrLabel as String] = "Vazie VPN"
        let added = SecItemAdd(add as CFDictionary, nil)
        guard added == errSecSuccess else { throw VaultError.keychain(added) }
        return fresh
    }
}

/// A key held in memory: for the tests, and for nothing else.
public final class MemoryKeyStore: VaultKeyStore, @unchecked Sendable {
    private let stored = SymmetricKey(size: .bits256)
    public init() {}
    public func key() throws -> SymmetricKey { stored }
}

/// `EncryptedVpnProfileRepository.kt`' storage half: the profiles as one AES-GCM document.
public struct Vault: Sendable {
    private struct Document: Codable {
        var version: Int
        var profiles: [StoredProfile]
    }

    private static let version = 1
    public let url: URL
    private let keys: VaultKeyStore

    public init(url: URL, keys: VaultKeyStore) {
        self.url = url
        self.keys = keys
    }

    /// The profiles, an empty list when nothing is stored yet. Throws when what is stored cannot be read: the caller
    /// must not write over it.
    public func read() throws -> [StoredProfile] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        do {
            let sealed = try Data(contentsOf: url)
            let box = try AES.GCM.SealedBox(combined: sealed)
            let plain = try AES.GCM.open(box, using: try keys.key())
            let document = try JSONDecoder().decode(Document.self, from: plain)
            guard document.version == Self.version else { throw VaultError.unreadable }
            return document.profiles
        } catch {
            // Not rethrown as it came and not logged: every error this can produce may carry the bytes that failed,
            // and those bytes are the person's configuration.
            throw VaultError.unreadable
        }
    }

    public func write(_ profiles: [StoredProfile]) throws {
        let plain = try JSONEncoder().encode(Document(version: Self.version, profiles: profiles))
        let sealed = try AES.GCM.seal(plain, using: try keys.key())
        guard let combined = sealed.combined else { throw VaultError.unreadable }
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try combined.write(to: url, options: .atomic)
        try? FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
    }

    /// Sets an unreadable file aside, so that the next write does not destroy what might still be recovered.
    public func quarantine() {
        let aside = url.deletingLastPathComponent().appendingPathComponent("\(url.lastPathComponent).unreadable-\(nowMillis())")
        try? FileManager.default.moveItem(at: url, to: aside)
    }
}

/// The stored profiles, decrypted once into memory and written back whole on every change, with every write
/// serialized (an actor does that).
public actor Repository {
    private let vault: Vault
    private var cache: [StoredProfile]?
    private let onChange: @Sendable () -> Void

    public init(vault: Vault, onChange: @escaping @Sendable () -> Void = {}) {
        self.vault = vault
        self.onChange = onChange
    }

    private func load() -> [StoredProfile] {
        if let cache { return cache }
        let list: [StoredProfile]
        do {
            list = try vault.read()
        } catch {
            vault.quarantine()
            list = []
        }
        cache = list
        return list
    }

    private func commit(_ list: [StoredProfile]) throws {
        try vault.write(list)
        cache = list
        onChange()
    }

    /// Newest first.
    public func summaries() -> [ProfileSummary] {
        load().sorted { $0.createdAt > $1.createdAt }.map(ProfileSummary.init)
    }

    public func profile(_ id: String) -> StoredProfile? { load().first { $0.id == id } }

    public enum RepositoryError: Error { case blankName }

    public func create(_ draft: ProfileDraft, name: String) throws -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { throw RepositoryError.blankName }
        let profile = StoredProfile(id: UUID().uuidString.lowercased(), name: trimmed, createdAt: nowMillis(), outbound: draft.outbound)
        try commit(load() + [profile])
        return profile.id
    }

    /// Several at once, in the order the list gave them, each created a millisecond apart so the order survives.
    public func createMany(_ drafts: [(draft: ProfileDraft, name: String)]) throws -> [String] {
        let base = nowMillis()
        let created = drafts.enumerated().map { index, item -> StoredProfile in
            let name = item.name.trimmingCharacters(in: .whitespacesAndNewlines)
            return StoredProfile(
                id: UUID().uuidString.lowercased(), name: name.isEmpty ? item.draft.outbound.endpoint.host : name,
                createdAt: base + Int64(drafts.count - index), outbound: item.draft.outbound)
        }
        try commit(load() + created)
        return created.map(\.id)
    }

    private func edit(_ id: String, _ transform: (inout StoredProfile) -> Void) throws {
        var list = load()
        guard let index = list.firstIndex(where: { $0.id == id }) else { return }
        let before = list[index]
        transform(&list[index])
        if list[index] == before { return }
        try commit(list)
    }

    /// Changes what a configuration is called, and nothing else.
    public func rename(_ id: String, to name: String) throws {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { throw RepositoryError.blankName }
        try edit(id) { $0.name = trimmed }
    }

    public func markUsed(_ id: String) throws {
        let now = nowMillis()
        try edit(id) { $0.lastUsedAt = now }
    }

    public func duplicate(_ id: String, name: String) throws -> String? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { throw RepositoryError.blankName }
        let list = load()
        guard let source = list.first(where: { $0.id == id }) else { return nil }
        // `lastUsedAt` is deliberately dropped: it describes what happened to the original.
        let copy = StoredProfile(id: UUID().uuidString.lowercased(), name: trimmed, createdAt: nowMillis(), lastUsedAt: nil, outbound: source.outbound)
        try commit(list + [copy])
        return copy.id
    }

    public func remove(_ id: String) throws {
        let list = load()
        let updated = list.filter { $0.id != id }
        if updated.count == list.count { return }
        try commit(updated)
    }

    public func details(_ id: String) -> ProfileDetails? { profile(id).map(ProfileDetails.init) }
}

/// The preferences: a plain file, because they are preferences and not secrets, and keeping them apart means a display
/// setting cannot break the store that holds the keys.
public struct PrefsStore: Sendable {
    public let url: URL

    public init(url: URL) { self.url = url }

    public func load() -> Prefs { Prefs.sanitize(try? Data(contentsOf: url)) }

    public func save(_ prefs: Prefs) {
        guard let data = try? JSONEncoder().encode(prefs) else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: .atomic)
    }
}
