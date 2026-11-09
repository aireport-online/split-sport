import Foundation

/// Build-time configuration of the app.
enum AppConfig {
    /// The server a fresh install talks to. People can point the app at another
    /// server with the "Server" field behind "Geavanceerd" on the login screen;
    /// that choice is kept in UserDefaults and wins over this default.
    #if DEBUG
    static let defaultServerURL = URL(string: "http://localhost:8787")!
    #else
    static let defaultServerURL = URL(string: "https://split.aireport.online")!
    #endif
}
