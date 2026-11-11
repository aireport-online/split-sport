import Foundation

/// Build-time configuration of the app.
enum AppConfig {
    /// The server a fresh install talks to. People can point the app at another
    /// server with the "Server" field behind "Geavanceerd" on the login screen;
    /// that choice is kept in UserDefaults and wins over this default.
    #if DEBUG
    static let defaultServerURL = URL(string: "http://localhost:8787")!
    #else
    static let defaultServerURL = releaseServerURL
    #endif

    /// The server of release builds. A placeholder that never resolves: set it to
    /// the real Split Sport server before shipping (see README).
    static let releaseServerURL = URL(string: "https://set-the-split-sport-server.invalid")!
}
