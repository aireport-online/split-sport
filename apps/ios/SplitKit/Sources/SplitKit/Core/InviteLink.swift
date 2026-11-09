import Foundation

/// Invite codes and the links that carry them.
public enum InviteLink {
    /// The link people share: `<server>/join/<code>`.
    public static func shareURL(server: URL, code: String) -> URL {
        server.appendingPathComponent("join").appendingPathComponent(code)
    }

    /// The code in `split://join/<code>` or `https://<server>/join/<code>`; nil for any other URL.
    public static func code(from url: URL) -> String? {
        var parts = url.pathComponents.filter { $0 != "/" }
        if url.scheme?.lowercased() == "split", let host = url.host {
            parts.insert(host, at: 0)
        }
        guard let joinAt = parts.lastIndex(where: { $0.lowercased() == "join" }), joinAt + 1 < parts.count else {
            return nil
        }
        let code = normalize(parts[joinAt + 1])
        return code.isEmpty ? nil : code
    }

    /// What a person typed or pasted, as a code: a whole invite link is accepted too.
    public static func code(fromInput input: String) -> String {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.contains("/"), let url = URL(string: trimmed), let code = code(from: url) {
            return code
        }
        return normalize(trimmed)
    }

    /// Codes are case-insensitive; the server stores them in capitals.
    private static func normalize(_ code: String) -> String {
        code.uppercased().filter { $0.isLetter || $0.isNumber }
    }
}
