import Foundation

extension String {
    /// Without leading and trailing whitespace.
    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
