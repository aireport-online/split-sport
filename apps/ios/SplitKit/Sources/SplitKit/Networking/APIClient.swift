import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// `SplitAPI` over HTTP. A client is immutable: a new one is made when the
/// server or the token changes.
public struct APIClient: SplitAPI {
    private let serverURL: URL
    private let token: String?
    private let transport: HTTPTransport
    private let onSessionExpired: @Sendable () -> Void

    /// - Parameters:
    ///   - serverURL: the server's base URL, without `/api`.
    ///   - token: the bearer token, or nil before logging in.
    ///   - onSessionExpired: called when the server answers 401 to a request that carried the token.
    public init(
        serverURL: URL,
        token: String?,
        transport: HTTPTransport = URLSessionTransport(),
        onSessionExpired: @escaping @Sendable () -> Void = {}
    ) {
        self.serverURL = serverURL
        self.token = token
        self.transport = transport
        self.onSessionExpired = onSessionExpired
    }

    // MARK: Account

    public func config() async throws -> ServerConfig {
        try await send("GET", "config")
    }

    public func register(name: String, email: String, password: String) async throws -> AuthResponse {
        try await send("POST", "auth/register", body: RegisterBody(name: name, email: email, password: password))
    }

    public func login(email: String, password: String) async throws -> AuthResponse {
        try await send("POST", "auth/login", body: LoginBody(email: email, password: password))
    }

    public func logout() async throws {
        try await sendWithoutResult("POST", "auth/logout")
    }

    public func me() async throws -> User {
        try await send("GET", "me")
    }

    public func updateMe(name: String, iban: String) async throws -> User {
        try await send("PATCH", "me", body: UpdateMeBody(name: name, iban: iban))
    }

    public func deleteAccount() async throws {
        try await sendWithoutResult("DELETE", "me")
    }

    // MARK: Groups and members

    public func groups() async throws -> [GroupSummary] {
        let list: GroupList = try await send("GET", "groups")
        return list.groups
    }

    public func createGroup(name: String, color: GroupColor?) async throws -> GroupDetail {
        try await send("POST", "groups", body: GroupBody(name: name, color: color))
    }

    public func group(id: String) async throws -> GroupDetail {
        try await send("GET", "groups/\(id)")
    }

    public func updateGroup(id: String, name: String?, color: GroupColor?) async throws -> GroupDetail {
        try await send("PATCH", "groups/\(id)", body: GroupBody(name: name, color: color))
    }

    public func leaveGroup(id: String) async throws {
        try await sendWithoutResult("POST", "groups/\(id)/leave")
    }

    public func addMember(groupId: String, name: String) async throws -> GroupDetail {
        try await send("POST", "groups/\(groupId)/members", body: NameBody(name: name))
    }

    public func renameMember(groupId: String, memberId: String, name: String) async throws -> GroupDetail {
        try await send("PATCH", "groups/\(groupId)/members/\(memberId)", body: NameBody(name: name))
    }

    public func removeMember(groupId: String, memberId: String) async throws -> GroupDetail {
        try await send("DELETE", "groups/\(groupId)/members/\(memberId)")
    }

    // MARK: Invites

    public func invite(code: String) async throws -> InvitePreview {
        try await send("GET", "invites/\(code)")
    }

    public func joinGroup(code: String, memberId: String?) async throws -> GroupDetail {
        try await send("POST", "invites/\(code)/join", body: JoinBody(memberId: memberId))
    }

    // MARK: Expenses

    public func expenses(groupId: String) async throws -> [Expense] {
        let list: ExpenseList = try await send("GET", "groups/\(groupId)/expenses")
        return list.expenses
    }

    public func createExpense(groupId: String, input: ExpenseInput) async throws -> Expense {
        try await send("POST", "groups/\(groupId)/expenses", body: input)
    }

    public func updateExpense(groupId: String, expenseId: String, input: ExpenseInput) async throws -> Expense {
        try await send("PUT", "groups/\(groupId)/expenses/\(expenseId)", body: input)
    }

    public func deleteExpense(groupId: String, expenseId: String) async throws {
        try await sendWithoutResult("DELETE", "groups/\(groupId)/expenses/\(expenseId)")
    }

    public func createSettlement(groupId: String, input: SettlementInput) async throws -> Expense {
        try await send("POST", "groups/\(groupId)/settlements", body: input)
    }

    // MARK: Receipts

    public func scanReceipt(jpegData: Data) async throws -> ReceiptScan {
        let body = ScanBody(imageBase64: jpegData.base64EncodedString(), mediaType: "image/jpeg")
        // Reading a receipt takes the server a while.
        return try await send("POST", "receipts/scan", body: body, timeout: 120)
    }

    // MARK: Plumbing

    private static let defaultTimeout: TimeInterval = 30

    private func send<Response: Decodable>(
        _ method: String, _ path: String, timeout: TimeInterval = APIClient.defaultTimeout
    ) async throws -> Response {
        try decode(try await perform(method, path, body: nil, timeout: timeout))
    }

    private func send<Body: Encodable, Response: Decodable>(
        _ method: String, _ path: String, body: Body, timeout: TimeInterval = APIClient.defaultTimeout
    ) async throws -> Response {
        try decode(try await perform(method, path, body: try encode(body), timeout: timeout))
    }

    private func sendWithoutResult(_ method: String, _ path: String) async throws {
        _ = try await perform(method, path, body: nil, timeout: APIClient.defaultTimeout)
    }

    /// Sends the request and returns the body of a 2xx answer; anything else is thrown as an `APIError`.
    private func perform(_ method: String, _ path: String, body: Data?, timeout: TimeInterval) async throws -> Data {
        var url = serverURL.appendingPathComponent("api")
        for component in path.split(separator: "/") {
            url.appendPathComponent(String(component))
        }
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }

        let (data, response) = try await transport.send(request)
        if (200..<300).contains(response.statusCode) { return data }

        // A 401 on a request without token is a wrong password, not an expired session.
        if response.statusCode == 401, token != nil {
            onSessionExpired()
            throw APIError.sessionExpired
        }
        if let envelope = try? JSONDecoder().decode(ErrorEnvelope.self, from: data) {
            throw APIError.server(status: response.statusCode, code: envelope.error.code, message: envelope.error.message)
        }
        throw APIError.invalidResponse
    }

    private func encode<Body: Encodable>(_ body: Body) throws -> Data {
        do {
            return try JSONEncoder().encode(body)
        } catch {
            throw APIError.invalidResponse
        }
    }

    private func decode<Response: Decodable>(_ data: Data) throws -> Response {
        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            throw APIError.invalidResponse
        }
    }
}

// MARK: - Wire formats that are not models

private struct ErrorEnvelope: Decodable {
    struct Detail: Decodable {
        let code: String
        let message: String
    }

    let error: Detail
}

private struct RegisterBody: Encodable {
    let name: String
    let email: String
    let password: String
}

private struct LoginBody: Encodable {
    let email: String
    let password: String
}

private struct UpdateMeBody: Encodable {
    let name: String
    let iban: String
}

/// Nil fields are left out, which the server reads as "do not change".
private struct GroupBody: Encodable {
    let name: String?
    let color: GroupColor?
}

private struct NameBody: Encodable {
    let name: String
}

private struct JoinBody: Encodable {
    let memberId: String?
}

private struct ScanBody: Encodable {
    let imageBase64: String
    let mediaType: String
}

private struct GroupList: Decodable {
    let groups: [GroupSummary]
}

private struct ExpenseList: Decodable {
    let expenses: [Expense]
}
