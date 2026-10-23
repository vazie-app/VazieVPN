import Foundation

/// Percent-decoding in the two strictnesses a share link needs (`PercentCoding.kt`).
public enum Percent {
    public static let userInfo = "-._~"
    /// Inside a query value: `/` and `:` are legal and common, `&` `=` `?` `#` would end the value.
    public static let query = "-._~/:,"
    /// A display name is last in the link, so only `#` itself has to go.
    public static let fragment = "-._~/:?&=+,!$'()*;@"

    /// Decoded text, or `nil` when a `%` is not followed by two hexadecimal digits.
    public static func decodeStrict(_ text: String) -> String? { decode(text, lenient: false) }

    /// Decoded text, with any malformed `%` kept as a literal `%`.
    public static func decodeLenient(_ text: String) -> String { decode(text, lenient: true) ?? text }

    public static func encode(_ text: String, safe: String) -> String {
        let hex = Array("0123456789ABCDEF")
        let safeBytes = Set(safe.utf8)
        var out = ""
        for byte in text.utf8 {
            if isUnreserved(byte) || safeBytes.contains(byte) {
                out.unicodeScalars.append(Unicode.Scalar(byte))
            } else {
                out.append("%")
                out.append(hex[Int(byte >> 4)])
                out.append(hex[Int(byte & 0xF)])
            }
        }
        return out
    }

    private static func isUnreserved(_ b: UInt8) -> Bool {
        (b >= 65 && b <= 90) || (b >= 97 && b <= 122) || (b >= 48 && b <= 57)
    }

    private static func hexValue(_ b: UInt8?) -> UInt8? {
        guard let b else { return nil }
        switch b {
        case 48...57: return b - 48
        case 97...102: return b - 87
        case 65...70: return b - 55
        default: return nil
        }
    }

    private static func decode(_ text: String, lenient: Bool) -> String? {
        guard text.contains("%") else { return text }
        let input = Array(text.utf8)
        var bytes: [UInt8] = []
        bytes.reserveCapacity(input.count)
        var index = 0
        while index < input.count {
            let b = input[index]
            if b != 0x25 {
                bytes.append(b)
                index += 1
                continue
            }
            let high = hexValue(index + 1 < input.count ? input[index + 1] : nil)
            let low = hexValue(index + 2 < input.count ? input[index + 2] : nil)
            guard let high, let low else {
                if !lenient { return nil }
                bytes.append(0x25)
                index += 1
                continue
            }
            bytes.append((high << 4) | low)
            index += 3
        }
        // Invalid UTF-8 becomes U+FFFD, as the other clients' decoders do.
        return String(decoding: bytes, as: UTF8.self)
    }
}

/// A share link's query string, read once and then consumed parameter by parameter (`LinkQuery.kt`).
public final class LinkQuery {
    private struct Entry {
        var name: String
        var value: String
        var consumed = false
    }

    private var entries: [Entry]

    private init(_ entries: [Entry]) { self.entries = entries }

    /// The value of the first unconsumed parameter matching any of `names`, in the order given.
    public func take(_ names: String...) -> String? {
        for name in names {
            if let i = entries.firstIndex(where: { !$0.consumed && $0.name.lowercased() == name.lowercased() }) {
                entries[i].consumed = true
                return entries[i].value
            }
        }
        return nil
    }

    /// Everything `take` did not claim, in the order it appeared, spelled as it arrived.
    public func remaining() -> [(String, String)] {
        entries.filter { !$0.consumed }.map { ($0.name, $0.value) }
    }

    /// `nil` when any name or value is malformed percent-encoding.
    public static func parse(_ query: String) -> LinkQuery? {
        if query.isEmpty { return LinkQuery([]) }
        var entries: [Entry] = []
        for pair in query.split(separator: "&", omittingEmptySubsequences: true) {
            let text = String(pair)
            let rawName: String
            let rawValue: String
            if let sep = text.firstIndex(of: "=") {
                rawName = String(text[..<sep])
                rawValue = String(text[text.index(after: sep)...])
            } else {
                rawName = text
                rawValue = ""
            }
            guard let name = Percent.decodeStrict(rawName), let value = Percent.decodeStrict(rawValue) else { return nil }
            if name.isEmpty { continue }
            entries.append(Entry(name: name, value: value))
        }
        return LinkQuery(entries)
    }
}
