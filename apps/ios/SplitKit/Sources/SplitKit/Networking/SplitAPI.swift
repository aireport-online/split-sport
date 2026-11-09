import Foundation

/// The Split REST API (`docs/api.md`). View models talk to this protocol so they
/// can be tested against a fake.
public protocol SplitAPI: Sendable {
    func config() async throws -> ServerConfig

    func register(name: String, email: String, password: String) async throws -> AuthResponse
    func login(email: String, password: String) async throws -> AuthResponse
    func logout() async throws
    func me() async throws -> User
    /// An empty `iban` clears it.
    func updateMe(name: String, iban: String) async throws -> User
    func deleteAccount() async throws

    func groups() async throws -> [GroupSummary]
    /// Without a colour the server picks the next one of the palette.
    func createGroup(name: String, color: GroupColor?) async throws -> GroupDetail
    func group(id: String) async throws -> GroupDetail
    func updateGroup(id: String, name: String?, color: GroupColor?) async throws -> GroupDetail
    func leaveGroup(id: String) async throws

    func addMember(groupId: String, name: String) async throws -> GroupDetail
    func renameMember(groupId: String, memberId: String, name: String) async throws -> GroupDetail
    func removeMember(groupId: String, memberId: String) async throws -> GroupDetail

    func invite(code: String) async throws -> InvitePreview
    /// With `memberId` the caller becomes that existing member without account.
    func joinGroup(code: String, memberId: String?) async throws -> GroupDetail

    func expenses(groupId: String) async throws -> [Expense]
    func createExpense(groupId: String, input: ExpenseInput) async throws -> Expense
    func updateExpense(groupId: String, expenseId: String, input: ExpenseInput) async throws -> Expense
    /// Also deletes a settlement.
    func deleteExpense(groupId: String, expenseId: String) async throws
    func createSettlement(groupId: String, input: SettlementInput) async throws -> Expense

    /// `jpegData` is a photo of a receipt, already scaled down by the caller.
    func scanReceipt(jpegData: Data) async throws -> ReceiptScan
}
