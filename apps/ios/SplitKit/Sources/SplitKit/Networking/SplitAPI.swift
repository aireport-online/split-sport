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
    /// Creates a team with its pot; the caller becomes treasurer. Without a colour the server picks the next one.
    func createGroup(name: String, color: GroupColor?, sport: Sport, club: String, season: String) async throws -> GroupDetail
    func group(id: String) async throws -> GroupDetail
    func updateGroup(id: String, patch: GroupPatch) async throws -> GroupDetail
    func leaveGroup(id: String) async throws

    /// The fine list (boetelijst) of a team.
    func addFine(groupId: String, name: String, amountCents: Int) async throws -> GroupDetail
    func updateFine(groupId: String, fineId: String, name: String, amountCents: Int) async throws -> GroupDetail
    func removeFine(groupId: String, fineId: String) async throws -> GroupDetail

    func addMember(groupId: String, name: String) async throws -> GroupDetail
    func renameMember(groupId: String, memberId: String, name: String) async throws -> GroupDetail
    func removeMember(groupId: String, memberId: String) async throws -> GroupDetail

    func invite(code: String) async throws -> InvitePreview
    /// With `memberId` the caller becomes that existing member without account.
    func joinGroup(code: String, memberId: String?) async throws -> GroupDetail

    func expenses(groupId: String) async throws -> [Expense]
    func createExpense(groupId: String, input: ExpenseInput) async throws -> Expense
    func updateExpense(groupId: String, expenseId: String, input: ExpenseInput) async throws -> Expense
    /// Also deletes a payment, a fine or a contribution.
    func deleteExpense(groupId: String, expenseId: String) async throws
    /// A fine or a contribution: every listed member owes the pot `amountCents`.
    func createCharge(groupId: String, input: ChargeInput) async throws -> Expense
    func createSettlement(groupId: String, input: SettlementInput) async throws -> Expense

    /// `jpegData` is a photo of a receipt, already scaled down by the caller.
    func scanReceipt(jpegData: Data) async throws -> ReceiptScan
}
