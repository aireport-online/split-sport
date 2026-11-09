import Foundation

/// Settings that survive a restart and are not secret.
public protocol Preferences: AnyObject {
    /// The server the person last logged in to, as typed in the "Server" field.
    var serverAddress: String? { get set }
}

/// `Preferences` in `UserDefaults`.
public final class UserDefaultsPreferences: Preferences {
    private static let serverAddressKey = "serverAddress"
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    public var serverAddress: String? {
        get { defaults.string(forKey: Self.serverAddressKey) }
        set { defaults.set(newValue, forKey: Self.serverAddressKey) }
    }
}
