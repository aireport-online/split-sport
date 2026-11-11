import XCTest
@testable import SplitKit

/// Exercises `APIClient` against a real Split Sport server. Skipped unless `SPLIT_API_URL`
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

        // A team: the server adds the pot, makes the creator penningmeester and gives a default fine list.
        var group = try await api.createGroup(
            name: "Heren 3", color: .teal, sport: .hockey, club: "MHC Oostpoort", season: Season.of(today)
        )
        XCTAssertEqual(group.color, .teal)
        XCTAssertEqual(group.sport, .hockey)
        XCTAssertEqual(group.club, "MHC Oostpoort")
        XCTAssertEqual(group.season, Season.of(today))
        XCTAssertEqual(group.members.map(\.name), ["Anna de Vries", "Teamkas"])
        XCTAssertEqual(group.members.map(\.kind), [.player, .pot])
        XCTAssertEqual(group.potMemberId, group.members.last?.id)
        XCTAssertEqual(group.treasurerMemberId, group.myMemberId)
        XCTAssertEqual(group.fines.first?.name, "Te laat")
        XCTAssertEqual(group.fines.first?.amountCents, 200)
        XCTAssertEqual(group.pot, PotStatus(cashCents: 0, toReceiveCents: 0, toPayCents: 0))

        group = try await api.addMember(groupId: group.id, name: "Bas")
        group = try await api.addMember(groupId: group.id, name: "Chris")
        group = try await api.renameMember(groupId: group.id, memberId: group.members[2].id, name: "Christiaan")
        group = try await api.updateGroup(id: group.id, patch: GroupPatch(name: "Heren 4", sport: .korfbal, club: ""))
        XCTAssertEqual(group.name, "Heren 4")
        XCTAssertEqual(group.sport, .korfbal)
        XCTAssertNil(group.club, "An empty club clears it")
        XCTAssertEqual(group.members.map(\.name), ["Anna de Vries", "Bas", "Christiaan", "Teamkas"], "The pot stays last")
        let (anna, bas, chris, pot) = (group.members[0].id, group.members[1].id, group.members[2].id, group.potMemberId)
        XCTAssertEqual(group.myMemberId, anna)
        XCTAssertEqual(group.players.map(\.id), [anna, bas, chris])

        let groups = try await api.groups()
        XCTAssertEqual(groups.map(\.id), [group.id])
        XCTAssertEqual(groups.first?.memberCount, 3, "The pot is not counted")
        XCTAssertEqual(groups.first?.sport, .korfbal)

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

        // Among members: Anna paid 9600 and owes 400 + 1250 + 333 + 900 = 2883. Nothing with the pot yet.
        group = try await api.group(id: group.id)
        XCTAssertEqual(group.totalSpentCents, 9600)
        XCTAssertEqual(group.balance(of: anna), Balance(memberId: anna, balanceCents: 6717, potCents: 0, peerCents: 6717))
        XCTAssertEqual(group.balance(of: bas)?.peerCents, -5717)
        XCTAssertEqual(group.balance(of: chris)?.peerCents, -1000)
        XCTAssertEqual(group.transfers.map(\.amountCents), [5717, 1000])

        // ---- The team pot ----------------------------------------------------

        // Everyone is asked for 25,00; Bas gets a fine from the fine list.
        let contribution = try await api.createCharge(groupId: group.id, input: ChargeInput(
            type: .contribution, description: "Inleg november", amountCents: 2500, memberIds: [anna, bas, chris], date: today
        ))
        XCTAssertEqual(contribution.kind, .contribution)
        XCTAssertEqual(contribution.amountCents, 7500)
        XCTAssertEqual(contribution.paidById, pot)
        XCTAssertEqual(contribution.shares.map(\.amountCents), [2500, 2500, 2500])

        let lateFine = try XCTUnwrap(group.fines.first)
        let fine = try await api.createCharge(groupId: group.id, input: ChargeInput(
            type: .fine, description: lateFine.name, amountCents: lateFine.amountCents, memberIds: [bas], date: today
        ))
        XCTAssertEqual(fine.kind, .fine)
        XCTAssertEqual(fine.shares.map(\.memberId), [bas])

        group = try await api.group(id: group.id)
        XCTAssertEqual(group.pot, PotStatus(cashCents: 0, toReceiveCents: 7700, toPayCents: 0), "Claims are not cash")
        XCTAssertEqual(group.balance(of: bas), Balance(memberId: bas, balanceCents: -8417, potCents: -2700, peerCents: -5717))
        XCTAssertEqual(group.fineStats, [FineStat(memberId: bas, count: 1, totalCents: 200)])
        XCTAssertEqual(group.totalSpentCents, 9600, "Charges are not expenses")

        // The pot cannot be charged, and a fine cannot be edited.
        do {
            _ = try await api.createCharge(groupId: group.id, input: ChargeInput(
                type: .fine, description: "x", amountCents: 100, memberIds: [pot], date: today
            ))
            XCTFail("Expected the pot to be refused")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "validation_error")
        }
        do {
            let input = ExpenseInput(description: "Te laat", amountCents: 100, date: today, paidById: bas, split: .equal(memberIds: [bas]))
            _ = try await api.updateExpense(groupId: group.id, expenseId: fine.id, input: input)
            XCTFail("Expected not_found")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "not_found")
        }

        // Bas pays the pot; the pot pays the referee; Chris buys balls for the team.
        let payment = try await api.createSettlement(
            groupId: group.id, input: SettlementInput(fromMemberId: bas, toMemberId: pot, amountCents: 2700, date: today)
        )
        XCTAssertEqual(payment.kind, .settlement)
        _ = try await api.createExpense(groupId: group.id, input: ExpenseInput(
            description: "Scheidsrechter", amountCents: 1500, date: today, paidById: pot, split: .equal(memberIds: [pot])
        ))
        _ = try await api.createExpense(groupId: group.id, input: ExpenseInput(
            description: "Wedstrijdballen", amountCents: 4000, date: today, paidById: chris, split: .equal(memberIds: [pot])
        ))

        group = try await api.group(id: group.id)
        XCTAssertEqual(group.pot, PotStatus(cashCents: 1200, toReceiveCents: 2500, toPayCents: 1500))
        XCTAssertEqual(group.balances.map(\.potCents), [-2500, 0, 1500, 1000])
        XCTAssertEqual(group.balances.map(\.peerCents), [6717, -5717, -1000, 0])
        XCTAssertEqual(group.transfers, [
            Transfer(fromMemberId: anna, toMemberId: pot, amountCents: 2500),
            Transfer(fromMemberId: pot, toMemberId: chris, amountCents: 1500),
            Transfer(fromMemberId: bas, toMemberId: anna, amountCents: 5717),
            Transfer(fromMemberId: chris, toMemberId: anna, amountCents: 1000),
        ], "First with the pot, then among members")
        XCTAssertEqual(group.totalSpentCents, 15100)
        XCTAssertEqual(group.member(anna)?.iban, "NL91ABNA0417164300")

        // The fine list and the penningmeester.
        group = try await api.addFine(groupId: group.id, name: "Eigen goal", amountCents: 250)
        let ownGoal = try XCTUnwrap(group.fines.last)
        XCTAssertEqual(ownGoal.name, "Eigen goal")
        group = try await api.updateFine(groupId: group.id, fineId: ownGoal.id, name: "Eigen doelpunt", amountCents: 300)
        XCTAssertEqual(group.fines.last, Fine(id: ownGoal.id, name: "Eigen doelpunt", amountCents: 300))
        group = try await api.removeFine(groupId: group.id, fineId: ownGoal.id)
        XCTAssertFalse(group.fines.contains { $0.id == ownGoal.id })
        group = try await api.updateGroup(id: group.id, patch: GroupPatch(treasurerMemberId: bas))
        XCTAssertEqual(group.treasurerMemberId, bas)
        do {
            _ = try await api.updateGroup(id: group.id, patch: GroupPatch(treasurerMemberId: pot))
            XCTFail("Expected the pot to be refused as penningmeester")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "validation_error")
        }

        // Settle every suggested payment; afterwards everyone, the pot included, is square.
        for transfer in group.transfers {
            let settlement = try await api.createSettlement(
                groupId: group.id,
                input: SettlementInput(fromMemberId: transfer.fromMemberId, toMemberId: transfer.toMemberId, amountCents: transfer.amountCents, date: today)
            )
            XCTAssertEqual(settlement.kind, .settlement)
            XCTAssertEqual(settlement.shares.map(\.memberId), [transfer.toMemberId])
        }
        group = try await api.group(id: group.id)
        XCTAssertTrue(group.balances.allSatisfy { $0.balanceCents == 0 && $0.potCents == 0 && $0.peerCents == 0 })
        XCTAssertTrue(group.transfers.isEmpty)
        XCTAssertEqual(group.pot, PotStatus(cashCents: 2200, toReceiveCents: 0, toPayCents: 0))

        var expenses = try await api.expenses(groupId: group.id)
        XCTAssertEqual(expenses.count, 13)
        XCTAssertEqual(expenses.filter { $0.kind == .settlement }.count, 5)
        XCTAssertEqual(expenses.filter { $0.kind == .fine }.count, 1)
        XCTAssertEqual(expenses.filter { $0.kind == .contribution }.count, 1)

        // Remove an expense, the fine and a payment.
        try await api.deleteExpense(groupId: group.id, expenseId: created[1].id)
        try await api.deleteExpense(groupId: group.id, expenseId: fine.id)
        try await api.deleteExpense(groupId: group.id, expenseId: payment.id)
        expenses = try await api.expenses(groupId: group.id)
        XCTAssertEqual(expenses.count, 10)
        group = try await api.group(id: group.id)
        XCTAssertEqual(group.fineStats, [])

        // A member that is in expenses cannot be removed.
        do {
            _ = try await api.removeMember(groupId: group.id, memberId: bas)
            XCTFail("Expected member_in_use")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "member_in_use")
        }

        // Invite: a second account sees the team with its sport, cannot claim the pot,
        // claims "Bas" and becomes that member.
        let other = try await anonymous.register(name: "Bas Jansen", email: "ios-\(UUID().uuidString.lowercased())@example.nl", password: "geheim-wachtwoord")
        let otherAPI = APIClient(serverURL: server, token: other.token)
        let preview = try await otherAPI.invite(code: group.inviteCode.lowercased())
        XCTAssertEqual(preview.groupId, group.id)
        XCTAssertEqual(preview.sport, .korfbal)
        XCTAssertNil(preview.club)
        XCTAssertEqual(preview.memberCount, 3)
        XCTAssertFalse(preview.alreadyMember)
        XCTAssertEqual(preview.unclaimedMembers.map(\.name), ["Bas", "Christiaan"], "The pot cannot be claimed")
        do {
            _ = try await otherAPI.joinGroup(code: group.inviteCode, memberId: pot)
            XCTFail("Expected member_taken")
        } catch {
            XCTAssertEqual((error as? APIError)?.code, "member_taken")
        }
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
