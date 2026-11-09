import XCTest
@testable import SplitKit

/// Exercises `APIClient` against a real Split server. Skipped unless `SPLIT_API_URL`
/// is set, e.g. `SPLIT_API_URL=http://localhost:8787 swift test --filter IntegrationTests`.
final class IntegrationTests: XCTestCase {
    private func serverURL() throws -> URL {
        let value = ProcessInfo.processInfo.environment["SPLIT_API_URL"] ?? ""
        try XCTSkipIf(value.isEmpty, "Set SPLIT_API_URL to run the integration test")
        return try XCTUnwrap(ServerAddress.parse(value), "SPLIT_API_URL is not a server address")
    }

    func testFullRoundTrip() async throws {
        let server = try serverURL()
        let anonymous = APIClient(serverURL: server, token: nil)
        let today = CalendarDay.today()

        // Config needs no token.
        _ = try await anonymous.config()

        // Register, then log in again with the same credentials.
        let email = "ios-\(UUID().uuidString.lowercased())@example.nl"
        let registered = try await anonymous.register(name: "Anna", email: email, password: "geheim-wachtwoord")
        XCTAssertEqual(registered.user.email, email)
        let loggedIn = try await anonymous.login(email: email, password: "geheim-wachtwoord")
        let api = APIClient(serverURL: server, token: loggedIn.token)
        let me = try await api.me()
        XCTAssertEqual(me.name, "Anna")

        // A wrong password is a server error with a Dutch message, not an expired session.
        do {
            _ = try await anonymous.login(email: email, password: "verkeerd-wachtwoord")
            XCTFail("Expected invalid_credentials")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "invalid_credentials")
        }

        // Profile.
        let withIBAN = try await api.updateMe(name: "Anna de Vries", iban: "nl91 abna 0417 1643 00")
        XCTAssertEqual(withIBAN.iban, "NL91ABNA0417164300")

        // Group with two members without account.
        var group = try await api.createGroup(name: "Weekendje Texel", color: .teal)
        XCTAssertEqual(group.color, .teal)
        XCTAssertEqual(group.members.map(\.name), ["Anna de Vries"])
        group = try await api.addMember(groupId: group.id, name: "Bas")
        group = try await api.addMember(groupId: group.id, name: "Chris")
        group = try await api.renameMember(groupId: group.id, memberId: group.members[2].id, name: "Christiaan")
        group = try await api.updateGroup(id: group.id, name: "Texel 2026", color: nil)
        XCTAssertEqual(group.name, "Texel 2026")
        XCTAssertEqual(group.members.map(\.name), ["Anna de Vries", "Bas", "Christiaan"])
        let (anna, bas, chris) = (group.members[0].id, group.members[1].id, group.members[2].id)
        XCTAssertEqual(group.myMemberId, anna)

        let groups = try await api.groups()
        XCTAssertEqual(groups.map(\.id), [group.id])
        XCTAssertEqual(groups.first?.memberCount, 3)

        // One expense in every split mode; the server must agree with the local computeShares.
        let splits: [(String, Int, Split)] = [
            ("Gelijk", 1000, .equal(memberIds: [anna, bas, chris])),
            ("Bedragen", 5000, .exact(amounts: [.init(memberId: anna, amountCents: 1250), .init(memberId: bas, amountCents: 3750)])),
            ("Aandelen", 1000, .shares(weights: [
                .init(memberId: anna, weight: 1), .init(memberId: bas, weight: 2), .init(memberId: chris, weight: 0),
            ])),
            ("Per regel", 2400, .items([
                SplitItem(name: "Pizza", amountCents: 1500, memberIds: [anna, bas]),
                SplitItem(name: "Wijn", amountCents: 500, memberIds: [chris]),
            ])),
        ]
        var created: [Expense] = []
        for (description, amountCents, split) in splits {
            let input = ExpenseInput(description: description, amountCents: amountCents, date: today, paidById: anna, split: split)
            let expense = try await api.createExpense(groupId: group.id, input: input)
            let local = try SplitMath.computeShares(amountCents: amountCents, split: split)
            XCTAssertEqual(expense.shares.map { SplitMath.Share(memberId: $0.memberId, amountCents: $0.amountCents) }, local, description)
            XCTAssertEqual(expense.splitMode, split.mode)
            XCTAssertEqual(expense.kind, .expense)
            XCTAssertEqual(expense.date, today)
            created.append(expense)
        }
        XCTAssertEqual(created[2].shares.map(\.weight), [1, 2])
        XCTAssertEqual(created[3].items.map(\.name), ["Pizza", "Wijn"])

        // Edit one.
        let edited = try await api.updateExpense(
            groupId: group.id, expenseId: created[0].id,
            input: ExpenseInput(description: "Gelijk (aangepast)", amountCents: 1200, date: today, paidById: anna, split: .equal(memberIds: [anna, bas, chris]))
        )
        XCTAssertEqual(edited.shares.map(\.amountCents), [400, 400, 400])

        // A split that does not add up is refused with the server's message.
        do {
            let input = ExpenseInput(
                description: "Fout", amountCents: 1000, date: today, paidById: anna,
                split: .exact(amounts: [.init(memberId: anna, amountCents: 400)])
            )
            _ = try await api.createExpense(groupId: group.id, input: input)
            XCTFail("Expected exact_sum_mismatch")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "exact_sum_mismatch")
            XCTAssertEqual(error.userMessage, SplitMath.SplitError.exactSumMismatch.message)
        }

        // Balances: Anna paid 9600 and owes 400 + 1250 + 333 + 900 = 2883.
        group = try await api.group(id: group.id)
        XCTAssertEqual(group.totalSpentCents, 9600)
        XCTAssertEqual(group.balanceCents(of: anna), 6717)
        XCTAssertEqual(group.balances.reduce(0) { $0 + $1.balanceCents }, 0)
        XCTAssertFalse(group.transfers.isEmpty)
        XCTAssertTrue(group.transfers.allSatisfy { $0.toMemberId == anna })
        XCTAssertEqual(group.member(anna)?.iban, "NL91ABNA0417164300")

        // Settle every suggested payment; afterwards everyone is square.
        for transfer in group.transfers {
            let settlement = try await api.createSettlement(
                groupId: group.id,
                input: SettlementInput(fromMemberId: transfer.fromMemberId, toMemberId: transfer.toMemberId, amountCents: transfer.amountCents, date: today)
            )
            XCTAssertEqual(settlement.kind, .settlement)
            XCTAssertEqual(settlement.shares.map(\.memberId), [transfer.toMemberId])
        }
        group = try await api.group(id: group.id)
        XCTAssertTrue(group.balances.allSatisfy { $0.balanceCents == 0 })
        XCTAssertTrue(group.transfers.isEmpty)

        var expenses = try await api.expenses(groupId: group.id)
        XCTAssertEqual(expenses.count, 6)
        XCTAssertEqual(expenses.filter { $0.kind == .settlement }.count, 2)

        // Delete an expense and a settlement.
        try await api.deleteExpense(groupId: group.id, expenseId: created[1].id)
        if let settlement = expenses.first(where: { $0.kind == .settlement }) {
            try await api.deleteExpense(groupId: group.id, expenseId: settlement.id)
        }
        expenses = try await api.expenses(groupId: group.id)
        XCTAssertEqual(expenses.count, 4)

        // A member that is in expenses cannot be removed.
        do {
            _ = try await api.removeMember(groupId: group.id, memberId: bas)
            XCTFail("Expected member_in_use")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "member_in_use")
        }

        // Invite: a second account sees the group, claims "Bas" and becomes that member.
        let other = try await anonymous.register(name: "Bas Jansen", email: "ios-\(UUID().uuidString.lowercased())@example.nl", password: "geheim-wachtwoord")
        let otherAPI = APIClient(serverURL: server, token: other.token)
        let preview = try await otherAPI.invite(code: group.inviteCode.lowercased())
        XCTAssertEqual(preview.groupId, group.id)
        XCTAssertFalse(preview.alreadyMember)
        XCTAssertEqual(preview.unclaimedMembers.map(\.name), ["Bas", "Christiaan"])
        let joined = try await otherAPI.joinGroup(code: group.inviteCode, memberId: bas)
        XCTAssertEqual(joined.myMemberId, bas)
        XCTAssertEqual(joined.memberName(bas), "Bas Jansen")
        let previewAgain = try await otherAPI.invite(code: group.inviteCode)
        XCTAssertTrue(previewAgain.alreadyMember)
        try await otherAPI.leaveGroup(id: group.id)
        try await otherAPI.deleteAccount()

        // Log out: the token stops working and the client reports an expired session.
        let expiry = expectation(description: "onSessionExpired is called")
        let expiring = APIClient(serverURL: server, token: registered.token, onSessionExpired: { expiry.fulfill() })
        try await expiring.logout()
        do {
            _ = try await expiring.me()
            XCTFail("Expected the session to be expired")
        } catch {
            XCTAssertEqual(error as? APIError, .sessionExpired)
        }
        await fulfillment(of: [expiry], timeout: 1)

        // Delete the account; its token is gone too.
        try await api.deleteAccount()
        do {
            _ = try await api.me()
            XCTFail("Expected the deleted account's token to be rejected")
        } catch {
            XCTAssertEqual(error as? APIError, .sessionExpired)
        }
    }

    func testUnreachableServerIsReportedAsOffline() async throws {
        _ = try serverURL()
        let nowhere = APIClient(serverURL: URL(string: "http://127.0.0.1:9")!, token: nil)
        do {
            _ = try await nowhere.config()
            XCTFail("Expected a connection error")
        } catch {
            XCTAssertEqual(error as? APIError, .offline)
        }
    }
}
