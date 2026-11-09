import Foundation
import Observation

/// Who is logged in, on which server, and the API client that goes with it.
/// The one object the whole app shares.
@MainActor
@Observable
public final class AppSession {
    public enum Phase: Equatable, Sendable {
        /// Checking the stored token.
        case launching
        /// There is a token, but the server could not be reached to check it.
        case unreachable(message: String)
        case signedOut
        case signedIn
    }

    /// Builds the API client for a server and token; replaced by a fake in tests.
    public typealias APIFactory = (_ serverURL: URL, _ token: String?, _ onSessionExpired: @escaping @Sendable () -> Void) -> SplitAPI

    public private(set) var phase: Phase = .launching
    public private(set) var user: User?
    public private(set) var serverURL: URL
    /// The client for the current server and token. View models take it when they are created.
    public private(set) var api: SplitAPI
    /// Whether this server can read receipts (`GET /config`).
    public private(set) var receiptScanEnabled = false
    /// A message for the login screen, such as "Je sessie is verlopen".
    public var notice: String?
    /// An invite code that arrived by link and still has to be shown.
    public var pendingInviteCode: String?

    @ObservationIgnored private let tokenStore: TokenStore
    @ObservationIgnored private let preferences: Preferences
    @ObservationIgnored private let makeAPI: APIFactory
    @ObservationIgnored private var token: String?

    public init(
        tokenStore: TokenStore,
        preferences: Preferences,
        defaultServerURL: URL,
        makeAPI: @escaping APIFactory = { APIClient(serverURL: $0, token: $1, onSessionExpired: $2) }
    ) {
        self.tokenStore = tokenStore
        self.preferences = preferences
        self.makeAPI = makeAPI
        let serverURL = preferences.serverAddress.flatMap(ServerAddress.parse) ?? defaultServerURL
        self.serverURL = serverURL
        self.api = makeAPI(serverURL, nil, {})
    }

    // MARK: Starting and ending a session

    /// Called at launch: resumes the stored session when its token is still valid.
    public func restore() async {
        guard let stored = tokenStore.loadToken() else {
            phase = .signedOut
            return
        }
        phase = .launching
        token = stored
        api = authenticatedAPI(token: stored)
        do {
            user = try await api.me()
            phase = .signedIn
            await loadConfig()
        } catch APIError.sessionExpired {
            end(notice: APIError.sessionExpired.message)
        } catch {
            phase = .unreachable(message: error.userMessage ?? APIError.offline.message ?? "")
        }
    }

    public func signIn(serverURL: URL, email: String, password: String) async throws {
        let auth = try await makeAPI(serverURL, nil, {}).login(email: email, password: password)
        await begin(auth, on: serverURL)
    }

    public func register(serverURL: URL, name: String, email: String, password: String) async throws {
        let auth = try await makeAPI(serverURL, nil, {}).register(name: name, email: email, password: password)
        await begin(auth, on: serverURL)
    }

    /// Logs out on this device at once; the server is told on a best-effort basis.
    public func signOut() async {
        let previous = api
        end(notice: nil)
        try? await previous.logout()
    }

    public func deleteAccount() async throws {
        try await api.deleteAccount()
        end(notice: nil)
    }

    /// Forgets the stored session without contacting the server (when it cannot be reached).
    public func forgetSession() {
        end(notice: nil)
    }

    /// After the profile was saved.
    public func userDidChange(_ user: User) {
        self.user = user
    }

    // MARK: Links

    /// Handles `split://join/<code>` and `https://<server>/join/<code>`. Returns false for other URLs.
    @discardableResult
    public func open(_ url: URL) -> Bool {
        guard let code = InviteLink.code(from: url) else { return false }
        pendingInviteCode = code
        return true
    }

    // MARK: Internals

    private func begin(_ auth: AuthResponse, on serverURL: URL) async {
        tokenStore.saveToken(auth.token)
        preferences.serverAddress = serverURL.absoluteString
        self.serverURL = serverURL
        token = auth.token
        api = authenticatedAPI(token: auth.token)
        user = auth.user
        notice = nil
        phase = .signedIn
        await loadConfig()
    }

    private func end(notice: String?) {
        tokenStore.deleteToken()
        token = nil
        user = nil
        receiptScanEnabled = false
        api = makeAPI(serverURL, nil, {})
        self.notice = notice
        phase = .signedOut
    }

    private func authenticatedAPI(token: String) -> SplitAPI {
        makeAPI(serverURL, token) { [weak self] in
            Task { @MainActor in self?.sessionExpired(token: token) }
        }
    }

    /// The server rejected `token`. Ignored when that session has already ended.
    private func sessionExpired(token expired: String) {
        guard token == expired else { return }
        end(notice: APIError.sessionExpired.message)
    }

    /// Scanning stays hidden when the server cannot be asked.
    private func loadConfig() async {
        receiptScanEnabled = (try? await api.config().receiptScanEnabled) ?? false
    }
}
