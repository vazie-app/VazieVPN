import CryptoKit
import Foundation
import Security

public enum Paths {
    /// `~/Library/Application Support/Vazie VPN`. The browser extension's helper keeps its engine in the same place,
    /// so a Mac that has one already has the other's engine.
    public static var support: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Vazie VPN", isDirectory: true)
    }

    public static func engineDirectory(_ version: String, root: URL = support) -> URL {
        root.appendingPathComponent("engine", isDirectory: true).appendingPathComponent(version, isDirectory: true)
    }

    public static func downloadedEngine(_ version: String, root: URL = support) -> URL {
        engineDirectory(version, root: root).appendingPathComponent("xray")
    }

    /// The engine inside the app bundle, when the build put one there.
    public static var bundledEngine: URL? {
        Bundle.main.resourceURL?.appendingPathComponent("engine/xray")
    }

    public static var vault: URL { support.appendingPathComponent("mac/profiles.vault") }
    public static var prefs: URL { support.appendingPathComponent("mac/prefs.json") }
    public static var linksCache: URL { support.appendingPathComponent("mac/cache-links.json") }
    public static var faqCache: URL { support.appendingPathComponent("mac/cache-faq.json") }
    public static var proxySnapshot: URL { support.appendingPathComponent("mac/system-proxy.json") }
    public static var networkSnapshot: URL { support.appendingPathComponent("mac/network-snapshot.json") }
}

/// The Xray engine: pinned by version and SHA-256 per platform, downloaded from its own GitHub release and checked
/// before it is ever run — the same discipline the Android build applies to libXray (`fetchAndVerifyLibXray`) and the
/// extension's helper applies to its copy. A build can also carry the engine inside the app.
/// Who signed this program: the Team ID the root supervisor accepts an engine from. Nothing when the build is unsigned or
/// ad hoc, in which case only the pinned hashes count.
public enum CodeIdentity {
    public static func teamIdentifier() -> String? {
        var code: SecCode?
        guard SecCodeCopySelf([], &code) == errSecSuccess, let code else { return nil }
        var staticCode: SecStaticCode?
        guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess, let staticCode else { return nil }
        var info: CFDictionary?
        guard SecCodeCopySigningInformation(staticCode, SecCSFlags(rawValue: kSecCSSigningInformation), &info) == errSecSuccess,
            let dictionary = info as? [String: Any]
        else { return nil }
        let team = dictionary[kSecCodeInfoTeamIdentifier as String] as? String
        // The shape the supervisor will accept; anything else is "no team".
        guard let team, team.count == 10, team.allSatisfy({ $0.isUppercase || $0.isNumber }) else { return nil }
        return team
    }
}

public enum Engine {
    /// The Xray-core release the engine is taken from: the one libXray v26.7.28 in the Android app is built on.
    public static let version = "v26.7.28"

    public struct Asset: Equatable, Sendable {
        public var arch: String
        public var file: String
        public var sha256: String
    }

    public static let assets: [Asset] = [
        Asset(arch: "arm64", file: "Xray-macos-arm64-v8a.zip", sha256: "9b99a351febe31b7e0c7f22deeb1577a1da0b98aaa51aec7fd17832e68cf63d6"),
        Asset(arch: "x86_64", file: "Xray-macos-64.zip", sha256: "812f7d9de6d3506795eabda2f6928ba301c632c3fe6fa39c52ea8e0ed9e4e244"),
    ]

    /// SHA-256 of the engine itself, unpacked and as upstream built it, for the builds this app may hand to the root
    /// supervisor when the copy is not signed by the app's own team (a download rather than the bundled, re-signed one).
    /// Only builds that were actually hashed are listed; a Mac whose build is missing here can use the tunnel for the
    /// whole Mac only with the engine inside a signed app.
    public static let pinnedBinaries: [String] = [
        // Xray-macos-arm64-v8a.zip v26.7.28
        "bd4154efa640c5b8e21f10b68afcc9177c4f1f543be3ec0485b10c499b2a4b27",
    ]

    public static var architecture: String {
        #if arch(arm64)
            return "arm64"
        #else
            return "x86_64"
        #endif
    }

    public static var asset: Asset? { assets.first { $0.arch == architecture } }

    public static func downloadURL(_ asset: Asset) -> URL {
        URL(string: "https://github.com/XTLS/Xray-core/releases/download/\(version)/\(asset.file)")!
    }

    /// The engine to run: the one inside the app, else the downloaded one.
    public static func binary(support: URL = Paths.support, bundled: URL? = Paths.bundledEngine) -> URL? {
        let fm = FileManager.default
        if let bundled, fm.isExecutableFile(atPath: bundled.path) { return bundled }
        let downloaded = Paths.downloadedEngine(version, root: support)
        return fm.isExecutableFile(atPath: downloaded.path) ? downloaded : nil
    }

    /// `Xray 26.7.28 (…)` from `xray version`, as the number alone.
    public static func runningVersion(of binary: URL) async -> String? {
        guard let output = await ProcessRunner.run(binary, ["version"], timeout: 5) else { return nil }
        let first = String(decoding: output, as: UTF8.self).split(separator: "\n").first.map(String.init) ?? ""
        let words = first.split(separator: " ")
        return words.count > 1 ? String(words[1]) : nil
    }

    public static func sha256(of file: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: file)
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try handle.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// Downloads the pinned engine, refuses it unless its SHA-256 is the pinned one, and unpacks the executable.
    /// `progress(done, total)` is called as bytes arrive.
    public static func install(
        into support: URL = Paths.support, session: URLSession = .shared, from url: URL? = nil, expecting sha: String? = nil,
        progress: @escaping @Sendable (Int64, Int64) -> Void = { _, _ in }
    ) async throws {
        guard let asset else { throw EngineInstallFailure.unsupportedPlatform }
        let directory = Paths.engineDirectory(version, root: support)
        do { try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true) } catch { throw EngineInstallFailure.disk }

        let downloaded: URL
        do {
            let delegate = DownloadProgress(progress)
            var request = URLRequest(url: url ?? downloadURL(asset))
            request.timeoutInterval = 300
            let (temporary, response) = try await session.download(for: request, delegate: delegate)
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                throw EngineInstallFailure.download
            }
            downloaded = temporary
        } catch let failure as EngineInstallFailure {
            throw failure
        } catch {
            throw EngineInstallFailure.download
        }
        defer { try? FileManager.default.removeItem(at: downloaded) }

        let actual: String
        do { actual = try sha256(of: downloaded) } catch { throw EngineInstallFailure.disk }
        guard actual == (sha ?? asset.sha256) else { throw EngineInstallFailure.checksum }

        let partial = directory.appendingPathComponent("extract.part", isDirectory: true)
        try? FileManager.default.removeItem(at: partial)
        do { try FileManager.default.createDirectory(at: partial, withIntermediateDirectories: true) } catch { throw EngineInstallFailure.disk }
        defer { try? FileManager.default.removeItem(at: partial) }

        guard let status = await ProcessRunner.status(URL(fileURLWithPath: "/usr/bin/unzip"), ["-o", "-j", "-q", downloaded.path, "xray", "-d", partial.path], timeout: 60),
            status == 0
        else { throw EngineInstallFailure.archive }
        let extracted = partial.appendingPathComponent("xray")
        guard FileManager.default.fileExists(atPath: extracted.path) else { throw EngineInstallFailure.archive }
        // The archive's hash vouches for the archive; the program inside has its own pinned hash, which is what the root
        // supervisor will compare. A download that unpacks to anything else is refused here, not at the password prompt.
        if sha == nil, !pinnedBinaries.isEmpty, asset.arch == "arm64" {
            guard let inner = try? sha256(of: extracted), pinnedBinaries.contains(inner) else { throw EngineInstallFailure.checksum }
        }
        do {
            try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: extracted.path)
            let destination = Paths.downloadedEngine(version, root: support)
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: extracted, to: destination)
        } catch {
            throw EngineInstallFailure.disk
        }
    }
}

extension EngineInstallFailure: Error {}

private final class DownloadProgress: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let report: @Sendable (Int64, Int64) -> Void
    private var last: Int64 = 0

    init(_ report: @escaping @Sendable (Int64, Int64) -> Void) { self.report = report }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        if totalBytesWritten - last >= 256 * 1024 || totalBytesWritten == totalBytesExpectedToWrite {
            last = totalBytesWritten
            report(totalBytesWritten, max(totalBytesExpectedToWrite, totalBytesWritten))
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {}
}

/// Runs short commands and gives up on them after a limit.
public enum ProcessRunner {
    /// The standard output of a command that succeeded in time, else `nil`.
    public static func run(_ executable: URL, _ arguments: [String], timeout: TimeInterval) async -> Data? {
        guard let result = await execute(executable, arguments, timeout: timeout), result.status == 0 else { return nil }
        return result.output
    }

    /// The exit status of a command that finished in time.
    public static func status(_ executable: URL, _ arguments: [String], timeout: TimeInterval) async -> Int32? {
        await execute(executable, arguments, timeout: timeout)?.status
    }

    public struct Result: Sendable {
        public var status: Int32
        public var output: Data
        public var error: Data
    }

    public static func execute(_ executable: URL, _ arguments: [String], timeout: TimeInterval) async -> Result? {
        await withCheckedContinuation { (continuation: CheckedContinuation<Result?, Never>) in
            let process = Process()
            process.executableURL = executable
            process.arguments = arguments
            let out = Pipe()
            let err = Pipe()
            process.standardInput = FileHandle.nullDevice
            process.standardOutput = out
            process.standardError = err

            let lock = NSLock()
            var finished = false
            var output = Data()
            var errors = Data()
            let group = DispatchGroup()
            group.enter()
            group.enter()
            DispatchQueue.global().async {
                let data = out.fileHandleForReading.readDataToEndOfFile()
                lock.lock(); output = data; lock.unlock()
                group.leave()
            }
            DispatchQueue.global().async {
                let data = err.fileHandleForReading.readDataToEndOfFile()
                lock.lock(); errors = data; lock.unlock()
                group.leave()
            }
            func finish(_ result: Result?) {
                lock.lock()
                if finished { lock.unlock(); return }
                finished = true
                lock.unlock()
                continuation.resume(returning: result)
            }
            process.terminationHandler = { process in
                group.notify(queue: .global()) {
                    lock.lock(); let o = output, e = errors; lock.unlock()
                    finish(Result(status: process.terminationStatus, output: o, error: e))
                }
            }
            do { try process.run() } catch {
                finish(nil)
                return
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + timeout) {
                if process.isRunning { process.terminate() }
                finish(nil)
            }
        }
    }
}
