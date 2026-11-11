import Foundation
import Security
import SplitKit

/// Keeps the session token in the Keychain.
final class KeychainTokenStore: TokenStore {
    private let service = "online.aireport.split.sport"
    private let account = "session-token"

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func loadToken() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    func saveToken(_ token: String) {
        deleteToken()
        var query = baseQuery
        query[kSecValueData as String] = Data(token.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let status = SecItemAdd(query as CFDictionary, nil)
        // Without the Keychain the session still works until the app is closed.
        assert(status == errSecSuccess, "Could not store the session token: \(status)")
    }

    func deleteToken() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
