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
        groupId: "g1", name: "Heren 3", color: .teal, sport: .hockey, club: "MHC Oostpoort", memberCount: 3,
        alreadyMember: false, unclaimedMembers: []
    )
    var scanResult = ReceiptScan(merchant: nil, date: nil, totalCents: nil, items: [])

    private(set) var lastCredentials: (name: String?, email: String, password: String)?
    private(set) var lastProfile: (name: String, iban: String)?
    private(set) var lastGroupPatch: GroupPatch?
    private(set) var lastFine: (id: String?, name: String?, amountCents: Int?)?
    private(set) var lastCharge: ChargeInput?
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

    func createGroup(name: String, color: GroupColor?, sport: Sport, club: String, season: String) async throws -> GroupDetail {
        lastGroupPatch = GroupPatch(name: name, color: color, sport: sport, club: club, season: season)
        try record("createGroup")
        return groupResult
    }

    func group(id: String) async throws -> GroupDetail {
        try record("group")
        return groupResult
    }

    func updateGroup(id: String, patch: GroupPatch) async throws -> GroupDetail {
        lastGroupPatch = patch
        try record("updateGroup")
        if let name = patch.name { groupResult.name = name }
        if let color = patch.color { groupResult.color = color }
        if let sport = patch.sport { groupResult.sport = sport }
        if let club = patch.club { groupResult.club = club.isEmpty ? nil : club }
        if let season = patch.season { groupResult.season = season.isEmpty ? nil : season }
        if let treasurer = patch.treasurerMemberId { groupResult.treasurerMemberId = treasurer }
        return groupResult
    }

    func addFine(groupId: String, name: String, amountCents: Int) async throws -> GroupDetail {
        lastFine = (nil, name, amountCents)
        try record("addFine")
        groupResult.fines.append(Fine(id: "f-\(groupResult.fines.count + 1)", name: name, amountCents: amountCents))
        return groupResult
    }

    func updateFine(groupId: String, fineId: String, name: String, amountCents: Int) async throws -> GroupDetail {
        lastFine = (fineId, name, amountCents)
        try record("updateFine")
        if let index = groupResult.fines.firstIndex(where: { $0.id == fineId }) {
            groupResult.fines[index] = Fine(id: fineId, name: name, amountCents: amountCents)
        }
        return groupResult
    }

    func removeFine(groupId: String, fineId: String) async throws -> GroupDetail {
        lastFine = (fineId, nil, nil)
        try record("removeFine")
        groupResult.fines.removeAll { $0.id == fineId }
        return groupResult
    }

    func leaveGroup(id: String) async throws {
        try record("leaveGroup")
    }

    func addMember(groupId: String, name: String) async throws -> GroupDetail {
        lastMemberName = name
        try record("addMember")
        // New team members come before the pot, which stays last.
        let pot = groupResult.members.removeLast()
        groupResult.members.append(Member(id: "m-\(name.lowercased())", name: name, userId: nil, iban: nil))
        groupResult.members.append(pot)
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

    func createCharge(groupId: String, input: ChargeInput) async throws -> Expense {
        lastCharge = input
        try record("createCharge")
        return Fixtures.expense(kind: input.type == .fine ? .fine : .contribution)
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
