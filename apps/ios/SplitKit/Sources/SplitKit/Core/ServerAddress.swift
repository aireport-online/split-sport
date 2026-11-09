import Foundation

/// Turns what a person typed in the "Server" field into the server's base URL.
public enum ServerAddress {
    /// "split.example.nl/" → `https://split.example.nl`; nil when it is not a usable address.
    /// A missing scheme becomes `https`, except for `localhost` and IP addresses, which get `http`.
    public static func parse(_ input: String) -> URL? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if !text.contains("://") {
            text = (isLocal(text) ? "http://" : "https://") + text
        }
        while text.hasSuffix("/") { text.removeLast() }
        if text.lowercased().hasSuffix("/api") { text.removeLast(4) }

        guard let url = URL(string: text), let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https", let host = url.host, !host.isEmpty
        else { return nil }
        return url
    }

    private static func isLocal(_ address: String) -> Bool {
        let host = address.prefix { $0 != ":" && $0 != "/" }
        return host == "localhost" || host.allSatisfy { $0.isNumber || $0 == "." }
    }
}
