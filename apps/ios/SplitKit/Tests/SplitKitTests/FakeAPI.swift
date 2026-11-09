import Foundation
@testable import SplitKit

/// An in-memory `SplitAPI`: answers with canned values and records what was asked.
/// Only used from the main actor in tests.
final class FakeAPI: SplitAPI, @unchecked Sendable {
    /// The endpoints that were called, in order.
    private(set) var calls: [String] = []
    /// When set, every call fails with this error.
    var failure: Error?

    var configResult = ServerConfig(receiptScanEnabled: true)
    var authResult = AuthResponse(token: "token-1", user: Fixtures.user)
    var userResult = Fixtures.user
    var groupsResult: [GroupSummary] = []
    var groupResult = Fixtures.group
    var expensesResult: [Expense] = []
    var inviteResult = InvitePreview(
        groupId: "g1", name: "Weekendje Texel", color: .teal, memberCount: 3, alreadyMember: false, unclaimedMembers: []
    )
    var scanResult = ReceiptScan(merchant: nil, date: nil, totalCents: nil, items: [])

    private(set) var lastCredentials: (name: String?, email: String, password: String)?
    private(set) var lastProfile: (name: String, iban: String)?
    private(set) var lastGroupChange: (name: String?, color: GroupColor?)?
    private(set) var lastMemberName: String?
    private(set) var lastMemberId: String?
    private(set) var lastJoin: (code: String, memberId: String?)?
    private(set) var lastExpenseInput: ExpenseInput?
    private(set) var lastExpenseId: String?
    private(set) var lastSettlement: SettlementInput?
    private(set) var lastScanData: Data?

    private func record(_ call: String) throws {
        calls.append(call)
        if let failure { throw failure }
    }

    func config() async throws -> ServerConfig {
        try record("config")
        return configResult
    }

    func register(name: String, email: String, password: String) async throws -> AuthResponse {
        lastCredentials = (name, email, password)
        try record("register")
        return authResult
    }

    func login(email: String, password: String) async throws -> AuthResponse {
        lastCredentials = (nil, email, password)
        try record("login")
        return authResult
    }

    func logout() async throws {
        try record("logout")
    }

    func me() async throws -> User {
        try record("me")
        return userResult
    }

    func updateMe(name: String, iban: String) async throws -> User {
        lastProfile = (name, iban)
        try record("updateMe")
        userResult = User(id: userResult.id, name: name, email: userResult.email, iban: iban.isEmpty ? nil : iban)
        return userResult
    }

    func deleteAccount() async throws {
        try record("deleteAccount")
    }

    func groups() async throws -> [GroupSummary] {
        try record("groups")
        return groupsResult
    }

    func createGroup(name: String, color: GroupColor?) async throws -> GroupDetail {
        lastGroupChange = (name, color)
        try record("createGroup")
        return groupResult
    }

    func group(id: String) async throws -> GroupDetail {
        try record("group")
        return groupResult
    }

    func updateGroup(id: String, name: String?, color: GroupColor?) async throws -> GroupDetail {
        lastGroupChange = (name, color)
        try record("updateGroup")
        if let name { groupResult.name = name }
        if let color { groupResult.color = color }
        return groupResult
    }

    func leaveGroup(id: String) async throws {
        try record("leaveGroup")
    }

    func addMember(groupId: String, name: String) async throws -> GroupDetail {
        lastMemberName = name
        try record("addMember")
        groupResult.members.append(Member(id: "m-\(name.lowercased())", name: name, userId: nil, iban: nil))
        return groupResult
    }

    func renameMember(groupId: String, memberId: String, name: String) async throws -> GroupDetail {
        lastMemberId = memberId
        lastMemberName = name
        try record("renameMember")
        if let index = groupResult.members.firstIndex(where: { $0.id == memberId }) {
            groupResult.members[index].name = name
        }
        return groupResult
    }

    func removeMember(groupId: String, memberId: String) async throws -> GroupDetail {
        lastMemberId = memberId
        try record("removeMember")
        groupResult.members.removeAll { $0.id == memberId }
        return groupResult
    }

    func invite(code: String) async throws -> InvitePreview {
        lastJoin = (code, nil)
        try record("invite")
        return inviteResult
    }

    func joinGroup(code: String, memberId: String?) async throws -> GroupDetail {
        lastJoin = (code, memberId)
        try record("joinGroup")
        return groupResult
    }

    func expenses(groupId: String) async throws -> [Expense] {
        try record("expenses")
        return expensesResult
    }

    func createExpense(groupId: String, input: ExpenseInput) async throws -> Expense {
        lastExpenseInput = input
        try record("createExpense")
        return Fixtures.expense()
    }

    func updateExpense(groupId: String, expenseId: String, input: ExpenseInput) async throws -> Expense {
        lastExpenseInput = input
        lastExpenseId = expenseId
        try record("updateExpense")
        return Fixtures.expense(id: expenseId)
    }

    func deleteExpense(groupId: String, expenseId: String) async throws {
        lastExpenseId = expenseId
        try record("deleteExpense")
        expensesResult.removeAll { $0.id == expenseId }
    }

    func createSettlement(groupId: String, input: SettlementInput) async throws -> Expense {
        lastSettlement = input
        try record("createSettlement")
        return Fixtures.expense(kind: .settlement)
    }

    func scanReceipt(jpegData: Data) async throws -> ReceiptScan {
        lastScanData = jpegData
        try record("scanReceipt")
        return scanResult
    }
}

/// A `TokenStore` that forgets everything when the test ends.
final class MemoryTokenStore: TokenStore {
    var token: String?

    init(token: String? = nil) {
        self.token = token
    }

    func loadToken() -> String? { token }
    func saveToken(_ token: String) { self.token = token }
    func deleteToken() { token = nil }
}

final class MemoryPreferences: Preferences {
    var serverAddress: String?

    init(serverAddress: String? = nil) {
        self.serverAddress = serverAddress
    }
}
