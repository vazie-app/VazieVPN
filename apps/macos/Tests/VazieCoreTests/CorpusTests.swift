import XCTest

@testable import VazieCore

/// Runs the corpus in `/shared/vless-vectors`, the one every client reads. The Android client and the extension run
/// the same files; a vector that one parser reads differently from the other fails here or there.
final class CorpusTests: XCTestCase {
    private static let corpus: URL = {
        // …/apps/macos/Tests/VazieCoreTests/CorpusTests.swift → …/shared/vless-vectors
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("shared/vless-vectors")
    }()

    private func vectors(_ group: String) throws -> [[String: Any]] {
        let data = try Data(contentsOf: Self.corpus.appendingPathComponent("\(group).json"))
        let file = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        return file["vectors"] as! [[String: Any]]
    }

    /// A key that is absent is not checked; a key that is `null` must be nil in the result too.
    private var compared = 0

    private func compare(_ label: String, _ expect: [String: Any], _ key: String, _ actual: Any?, _ problems: inout [String]) {
        guard let expected = expect[key] else { return }
        compared += 1
        let want: Any? = expected is NSNull ? nil : expected
        let same: Bool
        switch (want, actual) {
        case (nil, nil): same = true
        case let (w as String, a as String): same = w == a
        case let (w as Bool, a as Bool): same = w == a
        case let (w as NSNumber, a as Int): same = w.intValue == a
        case let (w as [String], a as [String]): same = w == a
        case let (w as [Any], a as [String]): same = (w as? [String]) == a
        default: same = false
        }
        if !same { problems.append("\(label): \(key) expected \(String(describing: want)), got \(String(describing: actual))") }
    }

    private func check(_ group: String, _ vector: [String: Any]) -> [String] {
        let label = "\(group)/\(vector["name"] as! String)"
        let expect = vector["expect"] as! [String: Any]
        let result = Parsers.parse(vector["input"] as! String)
        var problems: [String] = []
        switch result {
        case let .recognized(draft):
            if expect["kind"] as? String != "recognized" { return ["\(label): expected \(expect["kind"]!), got recognized"] }
            let o = draft.outbound
            compare(label, expect, "protocol", "vless", &problems)
            compare(label, expect, "host", o.endpoint.host, &problems)
            compare(label, expect, "port", o.endpoint.port, &problems)
            compare(label, expect, "security", o.security.kind.rawValue, &problems)
            compare(label, expect, "transport", o.transport.kind.rawValue, &problems)
            compare(label, expect, "flow", o.flow?.rawValue, &problems)
            compare(label, expect, "serverName", o.security.serverName, &problems)
            compare(label, expect, "fingerprint", o.security.fingerprint, &problems)
            compare(label, expect, "suggestedName", draft.suggestedName, &problems)
            compare(label, expect, "unknownParameterNames", o.unknownParameters.map(\.name), &problems)
            switch o.security {
            case let .tls(_, _, alpn, allowInsecure):
                compare(label, expect, "alpn", alpn, &problems)
                compare(label, expect, "allowInsecure", allowInsecure, &problems)
            case let .reality(_, _, _, _, spiderX):
                compare(label, expect, "spiderX", spiderX, &problems)
            case .none: break
            }
            switch o.transport {
            case let .tcp(header): compare(label, expect, "tcpHeaderType", header, &problems)
            case let .ws(path, host):
                compare(label, expect, "wsPath", path, &problems)
                compare(label, expect, "wsHost", host, &problems)
            case let .grpc(service, multi):
                compare(label, expect, "grpcServiceName", service, &problems)
                compare(label, expect, "grpcMultiMode", multi, &problems)
            }
        case let .unsupported(feature):
            if expect["kind"] as? String != "unsupported" { return ["\(label): expected \(expect["kind"]!), got unsupported"] }
            compare(label, expect, "protocol", "vless", &problems)
            compare(label, expect, "feature", feature.feature, &problems)
            compare(label, expect, "value", feature.value, &problems)
        case let .invalid(reason):
            if expect["kind"] as? String != "invalid" { return ["\(label): expected \(expect["kind"]!), got invalid(\(reason.name))"] }
            compare(label, expect, "reason", reason.name, &problems)
            if case let .unknownScheme(scheme) = reason { compare(label, expect, "scheme", scheme, &problems) }
            if case let .missingParameter(parameter) = reason { compare(label, expect, "parameter", parameter, &problems) }
            if case let .conflictingParameters(parameters) = reason { compare(label, expect, "parameters", parameters, &problems) }
        }
        return problems
    }

    func testTheSharedCorpusReadsAsAgreed() throws {
        for group in ["recognized", "unknown-parameters", "unsupported", "invalid"] {
            let list = try vectors(group)
            XCTAssertFalse(list.isEmpty, "\(group) contributed no vectors")
            let problems = list.flatMap { check(group, $0) }
            XCTAssertEqual(problems, [], "the shared corpus disagrees with this parser:\n" + problems.joined(separator: "\n"))
        }
        // A corpus that compared nothing would pass for the wrong reason.
        XCTAssertGreaterThan(compared, 150)
    }
}
