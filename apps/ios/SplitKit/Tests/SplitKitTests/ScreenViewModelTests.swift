import XCTest
@testable import SplitKit

@MainActor
final class AuthViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private let tokens = MemoryTokenStore()
    private lazy var session = AppSession(
        tokenStore: tokens, preferences: MemoryPreferences(), defaultServerURL: Fixtures.server
    ) { _, _, _ in self.api }

    func testStartsWithTheCurrentServer() {
        let model = AuthViewModel(session: session)
        XCTAssertEqual(model.serverAddress, "https://split.example.nl")
        XCTAssertEqual(model.mode, .login)
        XCTAssertFalse(model.canSubmit)
    }

    func testLoginTrimsTheEmailAndSignsIn() async {
        let model = AuthViewModel(session: session)
        model.email = " anna@example.nl "
        model.password = "geheim-wachtwoord"
        XCTAssertTrue(model.canSubmit)
        await model.submit()

        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(api.lastCredentials?.email, "anna@example.nl")
        XCTAssertNil(model.errorMessage)
        XCTAssertFalse(model.isSubmitting)
    }

    func testRegisterNeedsANameAndALongEnoughPassword() async {
        let model = AuthViewModel(session: session)
        model.mode = .register
        model.email = "anna@example.nl"
        model.password = "kort"
        XCTAssertFalse(model.canSubmit, "A name is required to register")

        model.name = "Anna"
        await model.submit()
        XCTAssertEqual(model.errorMessage, "Kies een wachtwoord van minstens 8 tekens.")
        XCTAssertEqual(api.calls, [])

        model.password = "geheim-wachtwoord"
        await model.submit()
        XCTAssertEqual(api.calls.first, "register")
        XCTAssertEqual(api.lastCredentials?.name, "Anna")
        XCTAssertEqual(session.phase, .signedIn)
    }

    func testShowsTheServerMessage() async {
        api.failure = APIError.server(status: 401, code: "invalid_credentials", message: "E-mailadres of wachtwoord klopt niet.")
        let model = AuthViewModel(session: session)
        model.email = "anna@example.nl"
        model.password = "fout-wachtwoord"
        await model.submit()
        XCTAssertEqual(model.errorMessage, "E-mailadres of wachtwoord klopt niet.")
        XCTAssertNotEqual(session.phase, .signedIn)
    }

    func testRejectsABrokenServerAddress() async {
        let model = AuthViewModel(session: session)
        model.email = "anna@example.nl"
        model.password = "geheim-wachtwoord"
        model.serverAddress = "ftp://nergens"
        await model.submit()
        XCTAssertEqual(model.errorMessage, "Dit serveradres klopt niet.")
        XCTAssertEqual(api.calls, [])
    }

    func testUsesTheTypedServer() async {
        let model = AuthViewModel(session: session)
        model.email = "anna@example.nl"
        model.password = "geheim-wachtwoord"
        model.serverAddress = "192.168.1.20:8787"
        await model.submit()
        XCTAssertEqual(session.serverURL.absoluteString, "http://192.168.1.20:8787")
    }
}

@MainActor
final class GroupsViewModelTests: XCTestCase {
    private let api = FakeAPI()

    func testLoadsGroupsAndAddsUpTheBalance() async {
        api.groupsResult = [
            Fixtures.summary(id: "g1", balance: 2000),
            Fixtures.summary(id: "g2", name: "Huis", balance: -750),
        ]
        let model = GroupsViewModel(api: api)
        XCTAssertFalse(model.hasLoaded)
        await model.load()

        XCTAssertTrue(model.hasLoaded)
        XCTAssertEqual(model.groups.map(\.id), ["g1", "g2"])
        XCTAssertEqual(model.totalBalanceCents, 1250)
        XCTAssertEqual(model.totalBalanceFigure, "+\u{00A0}€\u{00A0}12,50")
        XCTAssertEqual(model.totalBalanceText, "Je krijgt €\u{00A0}12,50")
        XCTAssertFalse(model.isLoading)
    }

    func testNoGroupsMeansSquare() async {
        let model = GroupsViewModel(api: api)
        await model.load()
        XCTAssertTrue(model.groups.isEmpty)
        XCTAssertEqual(model.totalBalanceFigure, "€\u{00A0}0,00")
        XCTAssertEqual(model.totalBalanceText, "Je staat quitte")
    }

    func testAFailedRefreshKeepsTheListAndShowsTheMessage() async {
        api.groupsResult = [Fixtures.summary()]
        let model = GroupsViewModel(api: api)
        await model.load()

        api.failure = APIError.offline
        await model.load()
        XCTAssertEqual(model.groups.count, 1)
        XCTAssertEqual(model.errorMessage, APIError.offline.message)

        api.failure = nil
        await model.load()
        XCTAssertNil(model.errorMessage)
    }

    func testACancelledRefreshIsNotAnError() async {
        api.failure = APIError.cancelled
        let model = GroupsViewModel(api: api)
        await model.load()
        XCTAssertNil(model.errorMessage)
    }
}

@MainActor
final class NewGroupViewModelTests: XCTestCase {
    private let api = FakeAPI()

    func testOffersTheNextColourAndTheRunningSeason() {
        let model = NewGroupViewModel(api: api, existingGroupCount: 0, today: Fixtures.today)
        XCTAssertEqual(model.color, .amber)
        XCTAssertEqual(model.season, "2026/27")
        XCTAssertEqual(model.sport, .voetbal)
        XCTAssertEqual(NewGroupViewModel(api: api, existingGroupCount: 9).color, .rose)
    }

    func testCreatesTheTeam() async {
        let model = NewGroupViewModel(api: api, existingGroupCount: 2, today: Fixtures.today)
        XCTAssertFalse(model.canSave)
        model.name = "  Dames 2 "
        model.sport = .hockey
        model.club = " MHC Oostpoort "
        model.color = .rose
        let created = await model.create()

        XCTAssertEqual(created?.id, "g1")
        XCTAssertEqual(api.lastGroupPatch, GroupPatch(
            name: "Dames 2", color: .rose, sport: .hockey, club: "MHC Oostpoort", season: "2026/27"
        ))
    }

    func testShowsWhyCreatingFailed() async {
        api.failure = Fixtures.serverError
        let model = NewGroupViewModel(api: api, existingGroupCount: 0)
        model.name = "Heren 3"
        let created = await model.create()
        XCTAssertNil(created)
        XCTAssertEqual(model.errorMessage, "Controleer wat je hebt ingevuld.")
    }
}

@MainActor
final class GroupDetailViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private let anna = Fixtures.anna.id
    private let bas = Fixtures.bas.id
    private let chris = Fixtures.chris.id
    private let pot = Fixtures.pot.id

    private func makeModel() -> GroupDetailViewModel {
        GroupDetailViewModel(api: api, groupId: "g1", name: "Texel", color: .blue)
    }

    private func share(_ memberId: String, _ cents: Int) -> ExpenseShare {
        ExpenseShare(memberId: memberId, amountCents: cents, weight: nil)
    }

    func testLoadsTheTeamAndItsEntries() async {
        api.expensesResult = [Fixtures.expense()]
        let model = makeModel()
        XCTAssertEqual(model.name, "Texel")
        await model.load()

        XCTAssertEqual(Set(api.calls), ["group", "expenses"])
        XCTAssertEqual(model.name, "Heren 3")
        XCTAssertEqual(model.color, .teal)
        XCTAssertEqual(model.expenses.count, 1)
        XCTAssertNil(model.errorMessage)
    }

    func testExpenseRowsSayWhoPaidAndWhatItMeansForYou() async throws {
        let day = CalendarDay(year: CalendarDay.today().year, month: 11, day: 2)
        api.expensesResult = [
            Fixtures.expense(id: "e1", date: day),
            Fixtures.expense(id: "e2", description: "Taxi", amountCents: 999, paidById: bas, date: day,
                             shares: [share(anna, 333), share(bas, 666)]),
            Fixtures.expense(id: "e3", description: "Bier", paidById: bas, date: day, shares: [share(chris, 3000)]),
        ]
        let model = makeModel()
        await model.load()
        let rows = try XCTUnwrap(model.expenseMonths.first).rows

        XCTAssertEqual(rows[0].title, "Boodschappen")
        XCTAssertEqual(rows[0].amount, "30,00", "Receipt lines print amounts without the euro sign")
        XCTAssertEqual(rows[0].parties, [Party(id: anna, name: "Anna", highlighter: .amber)])
        XCTAssertEqual(rows[0].detail, "betaalde · 2 nov")
        XCTAssertEqual(rows[0].impact, "je krijgt €\u{00A0}20,00")
        XCTAssertNil(rows[0].stamp)
        XCTAssertTrue(rows[0].isEditable)

        XCTAssertEqual(rows[1].parties.first?.highlighter, .rose)
        XCTAssertEqual(rows[1].impact, "je betaalt €\u{00A0}3,33")
        XCTAssertNil(rows[2].impact, "Nothing is shown when you are not in the expense")
    }

    func testRowsForThePotFinesContributionsAndPayments() async throws {
        let day = CalendarDay(year: CalendarDay.today().year, month: 11, day: 2)
        api.expensesResult = [
            Fixtures.expense(id: "e1", description: "Scheidsrechter", paidById: pot, date: day, shares: [share(pot, 3000)]),
            Fixtures.expense(id: "e2", description: "Wedstrijdballen", amountCents: 4000, paidById: anna, date: day,
                             shares: [share(pot, 4000)]),
            Fixtures.expense(id: "e3", kind: .fine, description: "Te laat", amountCents: 200, paidById: pot, date: day,
                             splitMode: .exact, shares: [share(bas, 200)]),
            Fixtures.expense(id: "e4", kind: .fine, description: "Niet afgemeld", amountCents: 2000, paidById: pot, date: day,
                             splitMode: .exact, shares: [share(anna, 500), share(bas, 500), share(chris, 500), share("m-gone", 500)]),
            Fixtures.expense(id: "e5", kind: .contribution, description: "Inleg november", amountCents: 7500, paidById: pot,
                             date: day, splitMode: .exact, shares: [share(anna, 2500), share(bas, 2500), share(chris, 2500)]),
            Fixtures.expense(id: "e6", kind: .settlement, description: "Betaling", amountCents: 1200, paidById: bas, date: day,
                             splitMode: .exact, shares: [share(pot, 1200)]),
            Fixtures.expense(id: "e7", kind: .settlement, description: "Betaling", amountCents: 4000, paidById: pot, date: day,
                             splitMode: .exact, shares: [share(chris, 4000)]),
        ]
        let model = makeModel()
        await model.load()
        let rows = try XCTUnwrap(model.expenseMonths.first).rows
        let teamkas = Party(id: pot, name: "Teamkas", highlighter: nil)

        // The pot paid: "Teamkas betaalde", and nothing changes for you.
        XCTAssertEqual(rows[0].parties, [teamkas])
        XCTAssertTrue(rows[0].parties[0].isPot)
        XCTAssertEqual(rows[0].detail, "betaalde · 2 nov")
        XCTAssertNil(rows[0].impact)
        XCTAssertTrue(rows[0].isEditable)

        // You advanced something for the team: the pot owes you.
        XCTAssertEqual(rows[1].impact, "je krijgt €\u{00A0}40,00")

        // A fine: the fined member highlighted, stamp BOETE.
        XCTAssertEqual(rows[2].title, "Te laat")
        XCTAssertEqual(rows[2].amount, "2,00")
        XCTAssertEqual(rows[2].parties, [Party(id: bas, name: "Bas", highlighter: .rose)])
        XCTAssertEqual(rows[2].detail, "2 nov")
        XCTAssertEqual(rows[2].stamp, .fine)
        XCTAssertFalse(rows[2].isEditable)

        // A fine for many: three names and "+1".
        XCTAssertEqual(rows[3].parties.map(\.name), ["Anna", "Bas", "Chris"])
        XCTAssertEqual(rows[3].hiddenPartyCount, 1)

        // A contribution: "5 × 25,00" and stamp INLEG.
        XCTAssertEqual(rows[4].title, "Inleg november")
        XCTAssertEqual(rows[4].amount, "75,00")
        XCTAssertEqual(rows[4].parties, [])
        XCTAssertEqual(rows[4].detail, "3 × 25,00 · 2 nov")
        XCTAssertEqual(rows[4].stamp, .contribution)
        XCTAssertFalse(rows[4].isEditable)

        // Payments to and from the pot.
        XCTAssertEqual(rows[5].title, "Bas betaalde Teamkas")
        XCTAssertEqual(rows[5].detail, "betaling · 2 nov")
        XCTAssertEqual(rows[5].stamp, .paid)
        XCTAssertFalse(rows[5].isEditable)
        XCTAssertEqual(rows[6].title, "Teamkas betaalde Chris")
        XCTAssertEqual(rows[6].parties, [teamkas])
    }

    func testEntriesAreGroupedByMonthNewestFirst() async {
        api.expensesResult = [
            Fixtures.expense(id: "e1", date: CalendarDay(year: 2026, month: 11, day: 9)),
            Fixtures.expense(id: "e2", date: CalendarDay(year: 2026, month: 11, day: 1)),
            Fixtures.expense(id: "e3", date: CalendarDay(year: 2026, month: 10, day: 28)),
            Fixtures.expense(id: "e4", date: CalendarDay(year: 2025, month: 11, day: 3)),
        ]
        let model = makeModel()
        await model.load()
        let months = model.expenseMonths

        XCTAssertEqual(months.map(\.heading), ["NOVEMBER 2026", "OKTOBER 2026", "NOVEMBER 2025"])
        XCTAssertEqual(months.map { $0.rows.map(\.id) }, [["e1", "e2"], ["e3"], ["e4"]])
        XCTAssertEqual(Set(months.map(\.id)).count, 3)
    }

    func testPotSummaryShowsCashClaimsAndThePenningmeester() async throws {
        let model = makeModel()
        XCTAssertNil(model.potSummary)
        await model.load()
        let summary = try XCTUnwrap(model.potSummary)

        XCTAssertEqual(summary.cash, "€\u{00A0}47,50")
        XCTAssertEqual(summary.toReceive, "12,00")
        XCTAssertEqual(summary.toPay, "40,00")
        XCTAssertEqual(summary.treasurer, Party(id: anna, name: "Anna", highlighter: .amber))
        XCTAssertEqual(summary.treasurerIBAN, "NL91ABNA0417164300")
        XCTAssertEqual(summary.treasurerIBANText, "NL91 ABNA 0417 1643 00")
    }

    func testPotSummaryWithoutPenningmeesterAndWithANegativeKasstand() async throws {
        api.groupResult.treasurerMemberId = nil
        api.groupResult.pot = PotStatus(cashCents: -500, toReceiveCents: 0, toPayCents: 0)
        let model = makeModel()
        await model.load()
        let summary = try XCTUnwrap(model.potSummary)
        XCTAssertNil(summary.treasurer)
        XCTAssertNil(summary.treasurerIBAN)
        XCTAssertEqual(summary.cash, "\u{2212}\u{00A0}€\u{00A0}5,00")
    }

    func testPotTransfersAreKeptApartFromTheOnesBetweenMembers() async {
        let model = makeModel()
        await model.load()
        let withPot = model.potTransferRows
        let amongMembers = model.peerTransferRows

        XCTAssertEqual(withPot.map(\.spoken), ["Bas betaalt Teamkas €\u{00A0}12,00", "Teamkas betaalt Chris €\u{00A0}40,00"])
        XCTAssertEqual(withPot.map(\.amount), ["12,00", "40,00"])
        XCTAssertTrue(withPot[0].receiver.isPot)
        XCTAssertTrue(withPot[1].payer.isPot)
        XCTAssertEqual(withPot[0].iban, "NL91ABNA0417164300", "Paying the pot goes to the penningmeester's IBAN")
        XCTAssertNil(withPot[1].iban, "Chris has no account, so no IBAN")

        XCTAssertEqual(amongMembers.map(\.spoken), ["Bas betaalt Anna €\u{00A0}10,00", "Chris betaalt Anna €\u{00A0}10,00"])
        XCTAssertEqual(amongMembers.map(\.payer.highlighter), [.rose, .green])
        XCTAssertEqual(amongMembers[0].iban, "NL91ABNA0417164300")
        XCTAssertEqual(amongMembers[0].ibanText, "NL91 ABNA 0417 1643 00")
        XCTAssertEqual(Set((withPot + amongMembers).map(\.id)).count, 4)
    }

    func testBalanceRowsShowWhatMembersHaveAmongEachOther() async {
        let model = makeModel()
        await model.load()
        let rows = model.balanceRows

        XCTAssertEqual(rows.map(\.party.name), ["Anna", "Bas", "Chris"], "The pot is not a team member")
        XCTAssertEqual(rows.map(\.isMe), [true, false, false])
        XCTAssertEqual(rows.map(\.party.highlighter), [.amber, .rose, .green])
        XCTAssertEqual(rows.map(\.hasAccount), [true, true, false])
        XCTAssertEqual(rows.map(\.balance), ["+\u{00A0}20,00", "\u{2212}\u{00A0}10,00", "\u{2212}\u{00A0}10,00"])
        XCTAssertEqual(rows.map(\.spokenBalance), ["krijgt €\u{00A0}20,00", "betaalt €\u{00A0}10,00", "betaalt €\u{00A0}10,00"])
    }

    func testFineRankingCrownsTheFirst() async {
        let model = makeModel()
        await model.load()
        let ranking = model.fineRanking

        XCTAssertEqual(ranking.map(\.party.name), ["Bas", "Anna"])
        XCTAssertEqual(ranking.map(\.count), ["3×", "1×"])
        XCTAssertEqual(ranking.map(\.total), ["9,00", "2,00"])
        XCTAssertEqual(ranking.map(\.isKing), [true, false])
        XCTAssertEqual(ranking.map(\.spoken), ["Bas, 3 boetes, €\u{00A0}9,00", "Anna, 1 boete, €\u{00A0}2,00"])

        api.groupResult.fineStats = []
        await model.load()
        XCTAssertTrue(model.fineRanking.isEmpty, "Hidden while nobody has a fine")
    }

    func testLoadFailureShowsTheServerMessage() async {
        api.failure = APIError.server(status: 404, code: "not_found", message: "Dit team bestaat niet of je bent er geen lid van.")
        let model = makeModel()
        await model.load()
        XCTAssertNil(model.detail)
        XCTAssertEqual(model.errorMessage, "Dit team bestaat niet of je bent er geen lid van.")
        XCTAssertTrue(model.expenseMonths.isEmpty)
        XCTAssertTrue(model.potTransferRows.isEmpty)
        XCTAssertTrue(model.balanceRows.isEmpty)
    }

    func testRemovingAnEntryReloadsTheTeam() async {
        let fine = Fixtures.expense(kind: .fine, description: "Te laat")
        api.expensesResult = [fine]
        let model = makeModel()
        await model.load()
        await model.delete(fine)

        XCTAssertEqual(api.lastExpenseId, "e1")
        XCTAssertTrue(model.expenses.isEmpty)
        XCTAssertEqual(api.calls.filter { $0 == "group" }.count, 2, "Balances are refreshed after a mutation")
        XCTAssertNil(model.actionError)
    }

    func testAFailedRemovalIsReported() async {
        let expense = Fixtures.expense()
        api.expensesResult = [expense]
        let model = makeModel()
        await model.load()
        api.failure = APIError.server(status: 404, code: "not_found", message: "Deze uitgave bestaat niet (meer).")
        await model.delete(expense)
        XCTAssertEqual(model.actionError, "Deze uitgave bestaat niet (meer).")
        XCTAssertEqual(model.expenses.count, 1)
    }
}

@MainActor
final class GroupSettingsViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private var changes: [GroupDetail] = []

    private func makeModel() -> GroupSettingsViewModel {
        GroupSettingsViewModel(api: api, group: Fixtures.group, serverURL: Fixtures.server) { self.changes.append($0) }
    }

    func testInviteLinkPointsAtTheServer() {
        XCTAssertEqual(makeModel().inviteURL.absoluteString, "https://split.example.nl/join/ABCD2345")
    }

    func testTeamMembersLeaveOutThePot() {
        let rows = makeModel().memberRows
        XCTAssertEqual(rows.map(\.party.name), ["Anna", "Bas", "Chris"])
        XCTAssertEqual(rows.map(\.isMe), [true, false, false])
        XCTAssertEqual(rows.map(\.isEditable), [false, false, true])
        XCTAssertEqual(rows.map(\.party.highlighter), [.amber, .rose, .green])
    }

    func testSavesNameClubAndSeasonTogether() async {
        let model = makeModel()
        XCTAssertEqual(model.club, "MHC Oostpoort")
        XCTAssertEqual(model.season, "2026/27")
        XCTAssertFalse(model.canSaveDetails, "Nothing changed yet")

        model.name = " Heren 4 "
        model.club = ""
        model.season = "2027/28"
        XCTAssertTrue(model.canSaveDetails)
        await model.saveDetails()

        XCTAssertEqual(api.lastGroupPatch, GroupPatch(name: "Heren 4", club: "", season: "2027/28"))
        XCTAssertEqual(model.detail.name, "Heren 4")
        XCTAssertNil(model.detail.club, "An emptied club is cleared")
        XCTAssertEqual(model.name, "Heren 4")
        XCTAssertEqual(model.club, "")
        XCTAssertEqual(changes.map(\.name), ["Heren 4"])
        XCTAssertFalse(model.canSaveDetails)
    }

    func testANameIsRequired() {
        let model = makeModel()
        model.name = "  "
        model.club = "Andere club"
        XCTAssertFalse(model.canSaveDetails)
    }

    func testChangesColourAndSportOnlyWhenTheyDiffer() async {
        let model = makeModel()
        await model.setColor(.teal)
        await model.setSport(.hockey)
        XCTAssertEqual(api.calls, [])

        await model.setColor(.coral)
        XCTAssertEqual(api.lastGroupPatch, GroupPatch(color: .coral))
        await model.setSport(.korfbal)
        XCTAssertEqual(api.lastGroupPatch, GroupPatch(sport: .korfbal))
        XCTAssertEqual(model.detail.color, .coral)
        XCTAssertEqual(model.detail.sport, .korfbal)
    }

    func testHandsThePotToAnotherPenningmeester() async {
        let model = makeModel()
        XCTAssertEqual(model.treasurerCandidates.map(\.name), ["Anna", "Bas", "Chris"], "The pot cannot keep itself")

        await model.setTreasurer(memberId: Fixtures.anna.id)
        XCTAssertEqual(api.calls, [], "Anna already is the penningmeester")

        await model.setTreasurer(memberId: Fixtures.bas.id)
        XCTAssertEqual(api.lastGroupPatch, GroupPatch(treasurerMemberId: Fixtures.bas.id))
        XCTAssertEqual(model.detail.treasurerMemberId, Fixtures.bas.id)
    }

    func testAddsALineToTheFineList() async {
        let model = makeModel()
        XCTAssertFalse(model.canAddFine)
        model.newFineName = " Eigen goal "
        XCTAssertFalse(model.canAddFine, "A fine needs an amount")
        model.newFineAmountText = "0"
        XCTAssertFalse(model.canAddFine, "A fine is more than nothing")
        model.newFineAmountText = "2,50"
        XCTAssertTrue(model.canAddFine)
        await model.addFine()

        XCTAssertEqual(api.lastFine?.name, "Eigen goal")
        XCTAssertEqual(api.lastFine?.amountCents, 250)
        XCTAssertEqual(model.detail.fines.map(\.name), ["Te laat", "Gele kaart", "Eigen goal"])
        XCTAssertEqual(model.newFineName, "")
        XCTAssertEqual(model.newFineAmountText, "")
        XCTAssertEqual(changes.count, 1)
    }

    func testChangesAndRemovesALineOfTheFineList() async {
        let model = makeModel()
        await model.updateFine(id: "f1", name: " Veel te laat ", amountText: "3")
        XCTAssertEqual(api.lastFine?.id, "f1")
        XCTAssertEqual(model.detail.fines.first, Fine(id: "f1", name: "Veel te laat", amountCents: 300))

        await model.updateFine(id: "f1", name: "Te laat", amountText: "gratis")
        XCTAssertEqual(model.errorMessage, "Geef de boete een naam en een geldig bedrag.")
        XCTAssertEqual(api.calls, ["updateFine"], "An unusable amount is not sent")

        await model.removeFine(id: "f2")
        XCTAssertEqual(api.lastFine?.id, "f2")
        XCTAssertEqual(model.detail.fines.map(\.id), ["f1"])
        XCTAssertNil(model.errorMessage)
    }

    func testAFailedFineKeepsWhatWasTyped() async {
        api.failure = Fixtures.serverError
        let model = makeModel()
        model.newFineName = "Eigen goal"
        model.newFineAmountText = "2,50"
        await model.addFine()
        XCTAssertEqual(model.newFineName, "Eigen goal")
        XCTAssertEqual(model.errorMessage, "Controleer wat je hebt ingevuld.")
    }

    func testAddsRenamesAndRemovesMembers() async {
        let model = makeModel()
        model.newMemberName = " Dana "
        await model.addMember()
        XCTAssertEqual(api.lastMemberName, "Dana")
        XCTAssertEqual(model.newMemberName, "")
        XCTAssertEqual(model.memberRows.map(\.party.name), ["Anna", "Bas", "Chris", "Dana"])
        XCTAssertEqual(model.memberRows.last?.party.highlighter, .coral, "The pot stays last, so colours do not shift")

        await model.renameMember(id: Fixtures.chris.id, to: "Christiaan ")
        XCTAssertEqual(api.lastMemberName, "Christiaan")
        XCTAssertEqual(model.detail.memberName(Fixtures.chris.id), "Christiaan")

        await model.removeMember(id: Fixtures.chris.id)
        XCTAssertEqual(api.lastMemberId, Fixtures.chris.id)
        XCTAssertEqual(model.memberRows.count, 3)
        XCTAssertEqual(changes.count, 3)
    }

    func testShowsWhyAMemberCannotBeRemoved() async {
        api.failure = APIError.server(status: 409, code: "member_in_use", message: "Dit teamlid staat nog in uitgaven en kan niet worden verwijderd.")
        let model = makeModel()
        await model.removeMember(id: Fixtures.chris.id)
        XCTAssertEqual(model.errorMessage, "Dit teamlid staat nog in uitgaven en kan niet worden verwijderd.")
        XCTAssertEqual(model.memberRows.count, 3)
        XCTAssertTrue(changes.isEmpty)
        XCTAssertFalse(model.isWorking)
    }

    func testAFailedAddKeepsTheTypedName() async {
        api.failure = Fixtures.serverError
        let model = makeModel()
        model.newMemberName = "Dana"
        await model.addMember()
        XCTAssertEqual(model.newMemberName, "Dana")
    }

    func testLeavingReportsSuccess() async {
        let model = makeModel()
        let left = await model.leave()
        XCTAssertTrue(left)
        XCTAssertEqual(api.calls, ["leaveGroup"])

        api.failure = APIError.offline
        let leftAgain = await model.leave()
        XCTAssertFalse(leftAgain)
        XCTAssertEqual(model.errorMessage, APIError.offline.message)
    }
}

@MainActor
final class SettlementViewModelTests: XCTestCase {
    private let api = FakeAPI()

    func testPrefillsASuggestedPayment() {
        let transfer = Transfer(fromMemberId: Fixtures.bas.id, toMemberId: Fixtures.anna.id, amountCents: 1000)
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: transfer, today: Fixtures.today)
        XCTAssertEqual(model.fromMemberId, Fixtures.bas.id)
        XCTAssertEqual(model.toMemberId, Fixtures.anna.id)
        XCTAssertEqual(model.amountText, "10,00")
        XCTAssertEqual(model.date, Fixtures.today)
        XCTAssertTrue(model.canSave)
    }

    func testWithoutSuggestionThePaymentIsFromYourself() {
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: nil, today: Fixtures.today)
        XCTAssertEqual(model.fromMemberId, Fixtures.anna.id)
        XCTAssertEqual(model.toMemberId, Fixtures.bas.id)
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
        XCTAssertFalse(model.canSave)
    }

    func testNeedsTwoDifferentMembersAndAPositiveAmount() {
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: nil, today: Fixtures.today)
        model.amountText = "5"
        model.toMemberId = Fixtures.anna.id
        XCTAssertEqual(model.validationMessage, "Kies twee verschillende partijen.")
        model.toMemberId = Fixtures.chris.id
        XCTAssertNil(model.validationMessage)
        model.amountText = "0"
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
        model.amountText = "1.000.000,00"
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
    }

    func testSavesTheSettlement() async {
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: nil, today: Fixtures.today)
        model.toMemberId = Fixtures.chris.id
        model.amountText = "12,50"
        let saved = await model.save()

        XCTAssertTrue(saved)
        XCTAssertEqual(api.lastSettlement, SettlementInput(
            fromMemberId: Fixtures.anna.id, toMemberId: Fixtures.chris.id, amountCents: 1250, date: Fixtures.today
        ))
    }

    func testShowsWhySavingFailed() async {
        api.failure = Fixtures.serverError
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: nil, today: Fixtures.today)
        model.amountText = "5"
        let saved = await model.save()
        XCTAssertFalse(saved)
        XCTAssertEqual(model.errorMessage, "Controleer wat je hebt ingevuld.")
        XCTAssertFalse(model.isSaving)
    }
}

@MainActor
final class JoinViewModelTests: XCTestCase {
    private let api = FakeAPI()

    func testLooksUpACodeAndJoinsStraightAway() async {
        let model = JoinViewModel(api: api)
        XCTAssertFalse(model.canLookUp)
        model.codeText = " abcd 2345 "
        await model.lookUp()

        XCTAssertEqual(api.lastJoin?.code, "ABCD2345")
        XCTAssertEqual(model.preview?.name, "Heren 3")
        XCTAssertEqual(model.preview?.sport, .hockey)
        XCTAssertEqual(model.preview?.club, "MHC Oostpoort")
        XCTAssertFalse(model.needsIdentity)
        XCTAssertTrue(model.canJoin)

        let group = await model.join()
        XCTAssertEqual(group?.id, "g1")
        XCTAssertNil(api.lastJoin?.memberId ?? nil)
    }

    func testAcceptsAPastedInviteLink() async {
        let model = JoinViewModel(api: api)
        model.codeText = "https://split.example.nl/join/abcd2345"
        await model.lookUp()
        XCTAssertEqual(api.lastJoin?.code, "ABCD2345")
    }

    func testAsksWhoYouAreWhenThereAreMembersWithoutAccount() async {
        api.inviteResult = InvitePreview(
            groupId: "g1", name: "Heren 3", color: .teal, sport: .hockey, club: nil, memberCount: 3, alreadyMember: false,
            unclaimedMembers: [.init(id: Fixtures.chris.id, name: "Chris")]
        )
        let model = JoinViewModel(api: api, code: "ABCD2345")
        await model.lookUpIfPrefilled()

        XCTAssertTrue(model.needsIdentity)
        XCTAssertFalse(model.canJoin, "First answer 'Wie ben jij?'")

        model.identity = .member(id: Fixtures.chris.id)
        XCTAssertTrue(model.canJoin)
        _ = await model.join()
        XCTAssertEqual(api.lastJoin?.memberId, Fixtures.chris.id)

        model.identity = .newMember
        _ = await model.join()
        XCTAssertNil(api.lastJoin?.memberId ?? nil)
    }

    func testAlreadyAMemberNeedsNoIdentity() async {
        api.inviteResult = InvitePreview(
            groupId: "g1", name: "Heren 3", color: .teal, sport: .hockey, club: nil, memberCount: 3, alreadyMember: true,
            unclaimedMembers: [.init(id: Fixtures.chris.id, name: "Chris")]
        )
        let model = JoinViewModel(api: api, code: "ABCD2345")
        await model.lookUpIfPrefilled()
        XCTAssertFalse(model.needsIdentity)
        XCTAssertTrue(model.canJoin)
    }

    func testAWrongCodeShowsTheServerMessage() async {
        api.failure = APIError.server(status: 404, code: "not_found", message: "Deze uitnodigingscode klopt niet.")
        let model = JoinViewModel(api: api, code: "XXXXXXXX")
        await model.lookUpIfPrefilled()
        XCTAssertNil(model.preview)
        XCTAssertEqual(model.errorMessage, "Deze uitnodigingscode klopt niet.")
        XCTAssertFalse(model.canJoin)
    }

    func testChoosingAnotherCodeStartsOver() async {
        let model = JoinViewModel(api: api, code: "ABCD2345")
        await model.lookUpIfPrefilled()
        model.chooseOtherCode()
        XCTAssertNil(model.preview)
        XCTAssertNil(model.identity)
    }

    func testAFailedJoinKeepsThePreview() async {
        let model = JoinViewModel(api: api, code: "ABCD2345")
        await model.lookUpIfPrefilled()
        api.failure = APIError.server(status: 409, code: "member_taken", message: "Dit lid is al aan iemand anders gekoppeld.")
        let group = await model.join()
        XCTAssertNil(group)
        XCTAssertNotNil(model.preview)
        XCTAssertEqual(model.errorMessage, "Dit lid is al aan iemand anders gekoppeld.")
    }
}

@MainActor
final class ProfileViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private let tokens = MemoryTokenStore()
    private lazy var session = AppSession(
        tokenStore: tokens, preferences: MemoryPreferences(), defaultServerURL: Fixtures.server
    ) { _, _, _ in self.api }

    private func signedInModel() async throws -> ProfileViewModel {
        try await session.signIn(serverURL: Fixtures.server, email: "anna@example.nl", password: "geheim-wachtwoord")
        return ProfileViewModel(session: session)
    }

    func testShowsTheUserWithAReadableIBAN() async throws {
        let model = try await signedInModel()
        XCTAssertEqual(model.name, "Anna")
        XCTAssertEqual(model.email, "anna@example.nl")
        XCTAssertEqual(model.iban, "NL91 ABNA 0417 1643 00")
        XCTAssertFalse(model.hasChanges)
        XCTAssertFalse(model.canSave)
    }

    func testSavesNameAndCompactIBAN() async throws {
        let model = try await signedInModel()
        model.name = "Anna de Vries "
        model.iban = "nl02 rabo 0123 4567 89"
        XCTAssertTrue(model.canSave)
        await model.save()

        XCTAssertEqual(api.lastProfile?.name, "Anna de Vries")
        XCTAssertEqual(api.lastProfile?.iban, "NL02RABO0123456789")
        XCTAssertEqual(session.user?.name, "Anna de Vries")
        XCTAssertEqual(model.iban, "NL02 RABO 0123 4567 89")
        XCTAssertFalse(model.hasChanges)
    }

    func testAnEmptyIBANClearsIt() async throws {
        let model = try await signedInModel()
        model.iban = "  "
        await model.save()
        XCTAssertEqual(api.lastProfile?.iban, "")
        XCTAssertNil(session.user?.iban)
    }

    func testShowsWhySavingFailed() async throws {
        let model = try await signedInModel()
        model.iban = "NL00"
        api.failure = APIError.server(status: 400, code: "validation_error", message: "Dit IBAN klopt niet.")
        await model.save()
        XCTAssertEqual(model.errorMessage, "Dit IBAN klopt niet.")
        XCTAssertEqual(session.user?.iban, "NL91ABNA0417164300")
    }

    func testSignOutAndDeleteEndTheSession() async throws {
        let model = try await signedInModel()
        await model.signOut()
        XCTAssertEqual(session.phase, .signedOut)

        let again = try await signedInModel()
        await again.deleteAccount()
        XCTAssertEqual(session.phase, .signedOut)
        XCTAssertEqual(api.calls.last, "deleteAccount")
    }

    func testAFailedDeleteKeepsTheSession() async throws {
        let model = try await signedInModel()
        api.failure = APIError.offline
        await model.deleteAccount()
        XCTAssertEqual(session.phase, .signedIn)
        XCTAssertEqual(model.errorMessage, APIError.offline.message)
    }
}
