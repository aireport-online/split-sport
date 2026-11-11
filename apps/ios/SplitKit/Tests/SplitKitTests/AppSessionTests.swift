import XCTest
@testable import SplitKit

@MainActor
final class AppSessionTests: XCTestCase {
    private let api = FakeAPI()
    private let tokens = MemoryTokenStore()
    private let preferences = MemoryPreferences()
    /// What the session asked the factory for: server, token and the expiry callback.
    private var built: [(server: URL, token: String?, expire: @Sendable () -> Void)] = []

    private func makeSession() -> AppSession {
        AppSession(tokenStore: tokens, preferences: preferences, defaultServerURL: Fixtures.server) { server, token, expire in
            self.built.append((server, token, expire))
            return self.api
        }
    }

    /// Lets the main-actor task of the expiry callback run.
    private func settle() async {
        for _ in 0..<5 { await Task.yield() }
        try? await Task.sleep(nanoseconds: 20_000_000)
    }

    func testStartsSignedOutWithoutAToken() async {
        let session = makeSession()
        XCTAssertEqual(session.phase, .launching)
        await session.restore()
        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertEqual(api.calls, [])
        XCTAssertEqual(session.serverURL, Fixtures.server)
    }

    func testUsesTheStoredServerAddress() {
        preferences.serverAddress = "http://192.168.1.20:8787"
        let session = makeSession()
        XCTAssertEqual(session.serverURL.absoluteString, "http://192.168.1.20:8787")
    }

    func testRestoresAStoredSession() async {
        tokens.token = "stored"
        let session = makeSession()
        await session.restore()

        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(session.user, Fixtures.user)
        XCTAssertTrue(session.receiptScanEnabled)
        XCTAssertEqual(api.calls, ["me", "config"])
        XCTAssertEqual(built.last?.token, "stored")
    }

    func testRestoreWithAnExpiredTokenReturnsToLogin() async {
        tokens.token = "stale"
        api.failure = APIError.sessionExpired
        let session = makeSession()
        await session.restore()

        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNil(tokens.token)
        XCTAssertEqual(session.notice, "Je sessie is verlopen. Log opnieuw in.")
    }

    func testRestoreWithoutConnectionKeepsTheToken() async {
        tokens.token = "stored"
        api.failure = APIError.offline
        let session = makeSession()
        await session.restore()

        XCTAssertEqual(session.phase, .unreachable(message: APIError.offline.message ?? ""))
        XCTAssertEqual(tokens.token, "stored")

        api.failure = nil
        await session.restore()
        XCTAssertEqual(session.phase, .signedIn)
    }

    func testSignInStoresTokenAndServer() async throws {
        let session = makeSession()
        await session.restore()
        let server = URL(string: "http://localhost:8787")!
        try await session.signIn(serverURL: server, email: "anna@example.nl", password: "geheim-wachtwoord")

        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(session.user, Fixtures.user)
        XCTAssertEqual(tokens.token, "token-1")
        XCTAssertEqual(preferences.serverAddress, "http://localhost:8787")
        XCTAssertEqual(session.serverURL, server)
        XCTAssertEqual(api.calls, ["login", "config"])
        XCTAssertEqual(built.last?.server, server)
        XCTAssertEqual(built.last?.token, "token-1")
    }

    func testFailedSignInChangesNothing() async {
        let session = makeSession()
        await session.restore()
        api.failure = APIError.server(status: 401, code: "invalid_credentials", message: "E-mailadres of wachtwoord klopt niet.")
        do {
            try await session.signIn(serverURL: Fixtures.server, email: "anna@example.nl", password: "fout")
            XCTFail("Expected an error")
        } catch {
            XCTAssertEqual(error.userMessage, "E-mailadres of wachtwoord klopt niet.")
        }
        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNil(tokens.token)
        XCTAssertNil(preferences.serverAddress)
    }

    func testRegisterSignsIn() async throws {
        let session = makeSession()
        try await session.register(serverURL: Fixtures.server, name: "Anna", email: "anna@example.nl", password: "geheim-wachtwoord")
        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(api.lastCredentials?.name, "Anna")
    }

    func testSignOutClearsTheTokenAndTellsTheServer() async throws {
        let session = makeSession()
        try await session.signIn(serverURL: Fixtures.server, email: "a@b.nl", password: "geheim-wachtwoord")
        await session.signOut()

        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNil(session.user)
        XCTAssertNil(tokens.token)
        XCTAssertNil(session.notice)
        XCTAssertEqual(api.calls.last, "logout")
        XCTAssertNil(built.last?.token ?? nil)
    }

    func testExpiryCallbackReturnsToLoginWithANotice() async throws {
        let session = makeSession()
        try await session.signIn(serverURL: Fixtures.server, email: "a@b.nl", password: "geheim-wachtwoord")
        let expire = try XCTUnwrap(built.last?.expire)

        expire()
        await settle()

        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNil(tokens.token)
        XCTAssertEqual(session.notice, "Je sessie is verlopen. Log opnieuw in.")
    }

    func testExpiryOfAnEarlierSessionIsIgnored() async throws {
        let session = makeSession()
        try await session.signIn(serverURL: Fixtures.server, email: "a@b.nl", password: "geheim-wachtwoord")
        let staleExpire = try XCTUnwrap(built.last?.expire)
        await session.signOut()
        api.authResult = AuthResponse(token: "token-2", user: Fixtures.user)
        try await session.signIn(serverURL: Fixtures.server, email: "a@b.nl", password: "geheim-wachtwoord")

        staleExpire()
        await settle()

        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(tokens.token, "token-2")
    }

    func testDeleteAccountEndsTheSession() async throws {
        let session = makeSession()
        try await session.signIn(serverURL: Fixtures.server, email: "a@b.nl", password: "geheim-wachtwoord")
        try await session.deleteAccount()
        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertNil(tokens.token)
        XCTAssertEqual(api.calls.last, "deleteAccount")
    }

    func testScanFollowsTheServerConfig() async throws {
        tokens.token = "stored"
        api.configResult = ServerConfig(receiptScanEnabled: false)
        let session = makeSession()
        await session.restore()
        XCTAssertFalse(session.receiptScanEnabled)
    }

    func testOpeningAnInviteLinkKeepsTheCode() throws {
        let session = makeSession()
        XCTAssertTrue(session.open(try XCTUnwrap(URL(string: "splitsport://join/abcd2345"))))
        XCTAssertEqual(session.pendingInviteCode, "ABCD2345")
        XCTAssertFalse(session.open(try XCTUnwrap(URL(string: "splitsport://settings"))))
        XCTAssertEqual(session.pendingInviteCode, "ABCD2345")
    }
}
