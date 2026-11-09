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
        XCTAssertEqual(model.totalBalanceText, "Je krijgt €\u{00A0}12,50")
        XCTAssertEqual(model.totalBalanceSign, .positive)
        XCTAssertFalse(model.isLoading)
    }

    func testNoGroupsMeansSquare() async {
        let model = GroupsViewModel(api: api)
        await model.load()
        XCTAssertTrue(model.groups.isEmpty)
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

    func testOffersTheNextColourOfThePalette() {
        XCTAssertEqual(NewGroupViewModel(api: api, existingGroupCount: 0).color, .blue)
        XCTAssertEqual(NewGroupViewModel(api: api, existingGroupCount: 9).color, .amber)
    }

    func testCreatesTheGroup() async {
        let model = NewGroupViewModel(api: api, existingGroupCount: 2)
        XCTAssertFalse(model.canSave)
        model.name = "  Weekendje Texel "
        model.color = .rose
        let created = await model.create()

        XCTAssertEqual(created?.id, "g1")
        XCTAssertEqual(api.lastGroupChange?.name, "Weekendje Texel")
        XCTAssertEqual(api.lastGroupChange?.color, .rose)
    }

    func testShowsWhyCreatingFailed() async {
        api.failure = Fixtures.serverError
        let model = NewGroupViewModel(api: api, existingGroupCount: 0)
        model.name = "Huis"
        let created = await model.create()
        XCTAssertNil(created)
        XCTAssertEqual(model.errorMessage, "Controleer wat je hebt ingevuld.")
    }
}

@MainActor
final class GroupDetailViewModelTests: XCTestCase {
    private let api = FakeAPI()

    private func makeModel() -> GroupDetailViewModel {
        GroupDetailViewModel(api: api, groupId: "g1", name: "Texel", color: .blue)
    }

    func testLoadsTheGroupAndItsExpenses() async {
        api.expensesResult = [Fixtures.expense()]
        let model = makeModel()
        XCTAssertEqual(model.name, "Texel")
        await model.load()

        XCTAssertEqual(Set(api.calls), ["group", "expenses"])
        XCTAssertEqual(model.name, "Weekendje Texel")
        XCTAssertEqual(model.color, .teal)
        XCTAssertEqual(model.expenses.count, 1)
        XCTAssertNil(model.errorMessage)
    }

    func testExpenseRowsSayWhoPaidAndWhatItMeansForYou() async throws {
        let year = CalendarDay.today().year
        let day = CalendarDay(year: year, month: 11, day: 2)
        api.expensesResult = [
            Fixtures.expense(id: "e1", date: day),
            Fixtures.expense(id: "e2", description: "Taxi", amountCents: 999, paidById: Fixtures.bas.id, date: day, shares: [
                ExpenseShare(memberId: Fixtures.anna.id, amountCents: 333, weight: nil),
                ExpenseShare(memberId: Fixtures.bas.id, amountCents: 666, weight: nil),
            ]),
            Fixtures.expense(id: "e3", description: "Bier", paidById: Fixtures.bas.id, date: day, shares: [
                ExpenseShare(memberId: Fixtures.chris.id, amountCents: 3000, weight: nil),
            ]),
            Fixtures.expense(id: "e4", kind: .settlement, description: "Betaling", amountCents: 1000, paidById: Fixtures.bas.id, date: day,
                             splitMode: .exact, shares: [ExpenseShare(memberId: Fixtures.anna.id, amountCents: 1000, weight: nil)]),
        ]
        let model = makeModel()
        await model.load()
        let rows = model.expenseRows

        XCTAssertEqual(rows[0].title, "Boodschappen")
        XCTAssertEqual(rows[0].subtitle, "Anna betaalde · 2 nov")
        XCTAssertEqual(rows[0].amount, "€\u{00A0}30,00")
        XCTAssertEqual(rows[0].impact, "je krijgt €\u{00A0}20,00")
        XCTAssertEqual(rows[0].impactSign, .positive)

        XCTAssertEqual(rows[1].impact, "je betaalt €\u{00A0}3,33")
        XCTAssertEqual(rows[1].impactSign, .negative)

        XCTAssertNil(rows[2].impact, "Nothing is shown when you are not in the expense")

        XCTAssertTrue(rows[3].isSettlement)
        XCTAssertEqual(rows[3].title, "Bas betaalde Anna")
        XCTAssertEqual(rows[3].subtitle, "2 nov")
        XCTAssertNil(rows[3].impact)
    }

    func testBalanceRowsUseTheMemberOrderForTints() async {
        let model = makeModel()
        await model.load()
        let rows = model.balanceRows

        XCTAssertEqual(rows.map(\.name), ["Anna (jij)", "Bas", "Chris"])
        XCTAssertEqual(rows.map(\.tint), [.blue, .amber, .teal])
        XCTAssertEqual(rows.map(\.initials), ["A", "B", "C"])
        XCTAssertEqual(rows.map(\.hasAccount), [true, true, false])
        XCTAssertEqual(rows.map(\.balance), ["krijgt €\u{00A0}20,00", "betaalt €\u{00A0}10,00", "betaalt €\u{00A0}10,00"])
        XCTAssertEqual(rows.map(\.sign), [.positive, .negative, .negative])
    }

    func testTransferRowsShowTheReceiversIBAN() async {
        let model = makeModel()
        await model.load()
        let rows = model.transferRows

        XCTAssertEqual(rows.map(\.text), ["Bas betaalt Anna €\u{00A0}10,00", "Chris betaalt Anna €\u{00A0}10,00"])
        XCTAssertEqual(rows[0].iban, "NL91ABNA0417164300")
        XCTAssertEqual(rows[0].ibanText, "NL91 ABNA 0417 1643 00")
        XCTAssertEqual(rows[0].receiverName, "Anna")
        XCTAssertEqual(Set(rows.map(\.id)).count, 2)
    }

    func testLoadFailureShowsTheServerMessage() async {
        api.failure = APIError.server(status: 404, code: "not_found", message: "Deze groep bestaat niet of je bent er geen lid van.")
        let model = makeModel()
        await model.load()
        XCTAssertNil(model.detail)
        XCTAssertEqual(model.errorMessage, "Deze groep bestaat niet of je bent er geen lid van.")
        XCTAssertTrue(model.expenseRows.isEmpty)
    }

    func testDeletingAnExpenseReloadsTheGroup() async {
        let expense = Fixtures.expense()
        api.expensesResult = [expense]
        let model = makeModel()
        await model.load()
        await model.delete(expense)

        XCTAssertEqual(api.lastExpenseId, "e1")
        XCTAssertTrue(model.expenses.isEmpty)
        XCTAssertEqual(api.calls.filter { $0 == "group" }.count, 2, "Balances are refreshed after a mutation")
        XCTAssertNil(model.actionError)
    }

    func testAFailedDeleteIsReported() async {
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

    func testMemberRowsMarkWhoCanBeEdited() {
        let rows = makeModel().memberRows
        XCTAssertEqual(rows.map(\.displayName), ["Anna (jij)", "Bas", "Chris"])
        XCTAssertEqual(rows.map(\.isEditable), [false, false, true])
        XCTAssertEqual(rows.map(\.tint), [.blue, .amber, .teal])
    }

    func testRenamesTheGroup() async {
        let model = makeModel()
        XCTAssertFalse(model.canSaveName, "Nothing changed yet")
        model.name = " Texel 2026 "
        XCTAssertTrue(model.canSaveName)
        await model.saveName()

        XCTAssertEqual(api.lastGroupChange?.name, "Texel 2026")
        XCTAssertNil(api.lastGroupChange?.color)
        XCTAssertEqual(model.detail.name, "Texel 2026")
        XCTAssertEqual(model.name, "Texel 2026")
        XCTAssertEqual(changes.map(\.name), ["Texel 2026"])
    }

    func testChangesTheColourOnlyWhenItDiffers() async {
        let model = makeModel()
        await model.setColor(.teal)
        XCTAssertEqual(api.calls, [])
        await model.setColor(.coral)
        XCTAssertEqual(api.lastGroupChange?.color, .coral)
        XCTAssertEqual(model.detail.color, .coral)
    }

    func testAddsRenamesAndRemovesMembers() async {
        let model = makeModel()
        model.newMemberName = " Dana "
        await model.addMember()
        XCTAssertEqual(api.lastMemberName, "Dana")
        XCTAssertEqual(model.newMemberName, "")
        XCTAssertEqual(model.detail.members.count, 4)

        await model.renameMember(id: Fixtures.chris.id, to: "Christiaan ")
        XCTAssertEqual(api.lastMemberName, "Christiaan")
        XCTAssertEqual(model.detail.memberName(Fixtures.chris.id), "Christiaan")

        await model.removeMember(id: Fixtures.chris.id)
        XCTAssertEqual(api.lastMemberId, Fixtures.chris.id)
        XCTAssertEqual(model.detail.members.count, 3)
        XCTAssertEqual(changes.count, 3)
    }

    func testShowsWhyAMemberCannotBeRemoved() async {
        api.failure = APIError.server(status: 409, code: "member_in_use", message: "Dit lid staat nog in uitgaven en kan niet worden verwijderd.")
        let model = makeModel()
        await model.removeMember(id: Fixtures.chris.id)
        XCTAssertEqual(model.errorMessage, "Dit lid staat nog in uitgaven en kan niet worden verwijderd.")
        XCTAssertEqual(model.detail.members.count, 3)
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
        XCTAssertEqual(model.validationMessage, "Kies twee verschillende leden.")
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
        XCTAssertEqual(model.preview?.name, "Weekendje Texel")
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
            groupId: "g1", name: "Weekendje Texel", color: .teal, memberCount: 3, alreadyMember: false,
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
            groupId: "g1", name: "Weekendje Texel", color: .teal, memberCount: 3, alreadyMember: true,
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
