import Foundation

/// Where the bearer token is kept between launches. The app stores it in the Keychain.
public protocol TokenStore: AnyObject {
    func loadToken() -> String?
    func saveToken(_ token: String)
    func deleteToken()
}
