import XCTest
@testable import SplitKit

final class SportTests: XCTestCase {
    func testElevenSportsWithTheirLabelsInPickerOrder() {
        XCTAssertEqual(Sport.allCases.map(\.rawValue), [
            "voetbal", "hockey", "korfbal", "volleybal", "handbal", "basketbal", "tennis", "padel", "rugby", "waterpolo", "overig",
        ])
        XCTAssertEqual(Sport.allCases.map(\.label), [
            "Voetbal", "Hockey", "Korfbal", "Volleybal", "Handbal", "Basketbal", "Tennis", "Padel", "Rugby", "Waterpolo", "Overig",
        ])
    }

    func testAnUnknownSportBecomesOverig() throws {
        let sports = try JSONDecoder().decode([Sport].self, from: Data(#"["padel","schaken"]"#.utf8))
        XCTAssertEqual(sports, [.padel, .overig])
        XCTAssertEqual(String(decoding: try JSONEncoder().encode([Sport.waterpolo]), as: UTF8.self), #"["waterpolo"]"#)
    }

    func testSeasonRunsFromJulyToJune() {
        XCTAssertEqual(Season.of(CalendarDay(year: 2026, month: 11, day: 9)), "2026/27")
        XCTAssertEqual(Season.of(CalendarDay(year: 2026, month: 7, day: 1)), "2026/27")
        XCTAssertEqual(Season.of(CalendarDay(year: 2026, month: 6, day: 30)), "2025/26")
        XCTAssertEqual(Season.of(CalendarDay(year: 2027, month: 1, day: 15)), "2026/27")
        XCTAssertEqual(Season.of(CalendarDay(year: 2099, month: 8, day: 1)), "2099/00")
        XCTAssertEqual(Season.of(CalendarDay(year: 2008, month: 9, day: 1)), "2008/09")
    }

    func testThePotIsAMemberWithoutHighlighter() {
        let parties = Fixtures.group.parties
        XCTAssertEqual(parties.map(\.name), ["Anna", "Bas", "Chris", "Teamkas"])
        XCTAssertEqual(parties.map(\.highlighter), [.amber, .rose, .green, nil])
        XCTAssertEqual(parties.map(\.isPot), [false, false, false, true])
        XCTAssertEqual(Fixtures.group.players.map(\.name), ["Anna", "Bas", "Chris"])
        XCTAssertTrue(Fixtures.pot.isPot)
        XCTAssertFalse(Fixtures.anna.isPot)
    }
}

final class TripCalculationTests: XCTestCase {
    func testKilometresTimesTheRate() throws {
        let trip = try XCTUnwrap(TripCalculation(kilometersText: "84", rateText: TripCalculation.defaultRateText))
        XCTAssertEqual(trip.rateCents, 23)
        XCTAssertEqual(trip.amountCents, 1932)
        XCTAssertEqual(trip.kilometers, "84")
        XCTAssertEqual(trip.description, "Rit (84 km)")
        XCTAssertEqual(trip.summary, "84 km × €\u{00A0}0,23 = €\u{00A0}19,32")
    }

    func testHalfKilometresAndRounding() throws {
        let half = try XCTUnwrap(TripCalculation(kilometersText: "12,5", rateText: "0,23"))
        XCTAssertEqual(half.amountCents, 288, "12,5 × 23 = 287,5 rounds half up")
        XCTAssertEqual(half.description, "Rit (12,5 km)")

        let precise = try XCTUnwrap(TripCalculation(kilometersText: "7.25", rateText: "0,19"))
        XCTAssertEqual(precise.amountCents, 138, "7,25 × 19 = 137,75")
        XCTAssertEqual(precise.kilometers, "7,25")

        let small = try XCTUnwrap(TripCalculation(kilometersText: "3,05", rateText: "0,23"))
        XCTAssertEqual(small.kilometers, "3,05")

        let long = try XCTUnwrap(TripCalculation(kilometersText: "1.234", rateText: "0,21"))
        XCTAssertEqual(long.kilometers, "1234")
        XCTAssertEqual(long.amountCents, 25_914)
    }

    func testNeedsPositiveNumbers() {
        XCTAssertNil(TripCalculation(kilometersText: "", rateText: "0,23"))
        XCTAssertNil(TripCalculation(kilometersText: "0", rateText: "0,23"))
        XCTAssertNil(TripCalculation(kilometersText: "veel", rateText: "0,23"))
        XCTAssertNil(TripCalculation(kilometersText: "84", rateText: ""))
        XCTAssertNil(TripCalculation(kilometersText: "84", rateText: "0"))
        XCTAssertNil(TripCalculation(kilometersText: "99999999999", rateText: "0,23"))
    }
}

@MainActor
final class ChargeViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private let anna = Fixtures.anna.id
    private let bas = Fixtures.bas.id
    private let chris = Fixtures.chris.id

    private func makeModel(_ type: ChargeType) -> ChargeViewModel {
        ChargeViewModel(api: api, group: Fixtures.group, type: type, today: Fixtures.today)
    }

    // MARK: Boete geven

    func testAFineStartsWithNobodyPickedAndTheFineList() {
        let model = makeModel(.fine)
        XCTAssertEqual(model.members.map(\.name), ["Anna", "Bas", "Chris"], "The pot cannot be fined")
        XCTAssertEqual(model.fines.map(\.name), ["Te laat", "Gele kaart"])
        XCTAssertEqual(model.selectedMemberIds, [])
        XCTAssertEqual(model.description, "")
        XCTAssertEqual(model.date, Fixtures.today)
        XCTAssertEqual(model.validationMessage, "Kies minstens één teamlid.")
        XCTAssertNil(model.totalText)
        XCTAssertFalse(model.canSave)
    }

    func testChoosingALineOfTheFineListFillsTheForm() {
        let model = makeModel(.fine)
        model.toggleMember(bas)
        XCTAssertEqual(model.validationMessage, "Kies een boete of geef een omschrijving.")

        model.choose(Fixtures.fines[1])
        XCTAssertEqual(model.description, "Gele kaart")
        XCTAssertEqual(model.amountText, "5,00")
        XCTAssertTrue(model.isChosen(Fixtures.fines[1]))
        XCTAssertFalse(model.isChosen(Fixtures.fines[0]))
        XCTAssertEqual(model.totalText, "1 × €\u{00A0}5,00 = €\u{00A0}5,00")
        XCTAssertTrue(model.canSave)

        model.amountText = "7,50"
        XCTAssertFalse(model.isChosen(Fixtures.fines[1]), "A changed amount is an own fine")
    }

    func testSavesOneFineForTheChosenMembersInTeamOrder() async {
        let model = makeModel(.fine)
        model.toggleMember(chris)
        model.toggleMember(anna)
        model.toggleMember(bas)
        model.toggleMember(bas)
        model.description = " Bal over het hek "
        model.amountText = "3"
        XCTAssertEqual(model.totalText, "2 × €\u{00A0}3,00 = €\u{00A0}6,00")
        let saved = await model.save()

        XCTAssertTrue(saved)
        XCTAssertEqual(api.calls, ["createCharge"])
        XCTAssertEqual(api.lastCharge, ChargeInput(
            type: .fine, description: "Bal over het hek", amountCents: 300, memberIds: [anna, chris], date: Fixtures.today
        ))
    }

    func testAFineNeedsAnAmount() async {
        let model = makeModel(.fine)
        model.toggleMember(bas)
        model.description = "Te laat"
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
        model.amountText = "0"
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
        let saved = await model.save()
        XCTAssertFalse(saved)
        XCTAssertEqual(model.errorMessage, "Vul een geldig bedrag in.")
        XCTAssertEqual(api.calls, [])
    }

    // MARK: Inleg vragen

    func testAContributionStartsWithEveryoneAndTheMonth() {
        let model = makeModel(.contribution)
        XCTAssertEqual(model.description, "Inleg november")
        XCTAssertEqual(model.selectedMemberIds, [anna, bas, chris], "Everyone but the pot")
        XCTAssertTrue(model.fines.isEmpty)
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.")
    }

    func testShowsTheLiveTotal() {
        let model = makeModel(.contribution)
        model.amountText = "25"
        XCTAssertEqual(model.totalText, "3 × €\u{00A0}25,00 = €\u{00A0}75,00")
        model.toggleMember(chris)
        XCTAssertEqual(model.totalText, "2 × €\u{00A0}25,00 = €\u{00A0}50,00")
        model.amountText = "abc"
        XCTAssertNil(model.totalText)
    }

    func testSavesTheContribution() async {
        let model = makeModel(.contribution)
        model.amountText = "25,00"
        model.date = CalendarDay(year: 2026, month: 11, day: 1)
        let saved = await model.save()

        XCTAssertTrue(saved)
        XCTAssertEqual(api.lastCharge, ChargeInput(
            type: .contribution, description: "Inleg november", amountCents: 2500, memberIds: [anna, bas, chris],
            date: CalendarDay(year: 2026, month: 11, day: 1)
        ))
    }

    func testAContributionNeedsADescriptionAndStaysBelowTheMaximum() {
        let model = makeModel(.contribution)
        model.amountText = "25"
        model.description = "  "
        XCTAssertEqual(model.validationMessage, "Geef de inleg een omschrijving.")
        model.description = "Inleg"
        model.amountText = "400.000,00"
        XCTAssertEqual(model.validationMessage, "Vul een geldig bedrag in.", "Three times this is more than the server takes")
    }

    func testShowsWhySavingFailed() async {
        api.failure = APIError.server(status: 400, code: "unknown_member", message: "Een van de teamleden hoort niet bij dit team.")
        let model = makeModel(.contribution)
        model.amountText = "25"
        let saved = await model.save()
        XCTAssertFalse(saved)
        XCTAssertEqual(model.errorMessage, "Een van de teamleden hoort niet bij dit team.")
        XCTAssertFalse(model.isSaving)
    }
}

/// The expense form with the team pot in it.
@MainActor
final class ExpenseEditorPotTests: XCTestCase {
    private let api = FakeAPI()
    private let anna = Fixtures.anna.id
    private let bas = Fixtures.bas.id
    private let chris = Fixtures.chris.id
    private let pot = Fixtures.pot.id

    private func makeModel(editing expense: Expense? = nil) -> ExpenseEditorViewModel {
        ExpenseEditorViewModel(api: api, group: Fixtures.group, expense: expense, canScanReceipt: false, today: Fixtures.today)
    }

    private func amounts(_ model: ExpenseEditorViewModel) -> [String: Int] {
        guard case .shares(let shares) = model.preview else { return [:] }
        return Dictionary(uniqueKeysWithValues: shares.map { ($0.id, $0.amountCents) })
    }

    func testThePotIsInTheListsButUntickedByDefault() {
        let model = makeModel()
        XCTAssertEqual(model.members.map(\.name), ["Anna", "Bas", "Chris", "Teamkas"])
        XCTAssertTrue(model.members[3].isPot)
        XCTAssertFalse(model.equalMemberIds.contains(pot))
        XCTAssertEqual(model.weight(for: pot), 0)
        XCTAssertEqual(model.weight(for: anna), 1)

        model.amountText = "9"
        XCTAssertEqual(amounts(model), [anna: 300, bas: 300, chris: 300])
    }

    func testPaidFromThePot() async {
        let model = makeModel()
        model.description = "Scheidsrechter"
        model.amountText = "30"
        model.paidById = pot
        for id in [anna, bas, chris, pot] { model.toggleEqualMember(id) }
        XCTAssertEqual(amounts(model), [pot: 3000], "Ticking only the Teamkas: ten laste van de teamkas")
        guard case .shares(let shares) = model.preview else { return XCTFail("Expected shares") }
        XCTAssertTrue(shares[0].party.isPot)

        let saved = await model.save()
        XCTAssertTrue(saved)
        XCTAssertEqual(api.lastExpenseInput, ExpenseInput(
            description: "Scheidsrechter", amountCents: 3000, date: Fixtures.today, paidById: pot,
            split: .equal(memberIds: [pot])
        ))
    }

    func testThePotCanShareWithMembers() {
        let model = makeModel()
        model.amountText = "40"
        model.toggleEqualMember(pot)
        XCTAssertEqual(amounts(model), [anna: 1000, bas: 1000, chris: 1000, pot: 1000])

        model.mode = .shares
        model.setWeight(2, for: pot)
        XCTAssertEqual(amounts(model), [anna: 800, bas: 800, chris: 800, pot: 1600])
    }

    func testEditingAnExpenseForThePot() {
        let expense = Fixtures.expense(
            description: "Wedstrijdballen", amountCents: 4000, paidById: chris,
            shares: [ExpenseShare(memberId: pot, amountCents: 4000, weight: nil)]
        )
        let model = makeModel(editing: expense)
        XCTAssertEqual(model.equalMemberIds, [pot])
        XCTAssertEqual(model.paidById, chris)
        XCTAssertEqual(model.split, .equal(memberIds: [pot]))
    }

    func testReceiptLinesStartWithTheTeamMembersOnly() async {
        api.scanResult = ReceiptScan(merchant: "Kantine", date: nil, totalCents: 900, items: [.init(name: "Bier", amountCents: 900)])
        let model = makeModel()
        model.addItem()
        XCTAssertEqual(model.items[0].memberIds, [anna, bas, chris])
        XCTAssertEqual(model.colors(of: model.items[0]), [.amber, .rose, .green])

        model.toggleMember(pot, inItem: model.items[0].id)
        XCTAssertEqual(model.colors(of: model.items[0]), [.amber, .rose, .green], "The pot has no highlighter to swipe with")

        await model.scanReceipt(jpegData: Data())
        XCTAssertEqual(model.items.map(\.memberIds), [[anna, bas, chris]])
    }

    // MARK: Rit berekenen

    func testTheTripHelperFillsTheAmountAndAnEmptyDescription() {
        let model = makeModel()
        XCTAssertEqual(model.tripRateText, "0,23")
        XCTAssertNil(model.trip)
        model.applyTrip()
        XCTAssertEqual(model.amountText, "", "Nothing happens without kilometres")

        model.tripKilometersText = "84"
        XCTAssertEqual(model.trip?.summary, "84 km × €\u{00A0}0,23 = €\u{00A0}19,32")
        model.applyTrip()
        XCTAssertEqual(model.amountText, "19,32")
        XCTAssertEqual(model.description, "Rit (84 km)")
        XCTAssertEqual(model.amountCents, 1932)
    }

    func testTheTripHelperKeepsATypedDescriptionAndFollowsTheRate() {
        let model = makeModel()
        model.description = "Uitwedstrijd Zwolle"
        model.tripKilometersText = "120"
        model.tripRateText = "0,30"
        model.applyTrip()
        XCTAssertEqual(model.description, "Uitwedstrijd Zwolle")
        XCTAssertEqual(model.amountText, "36,00")
    }
}

/// The payment sheet with the team pot in it.
@MainActor
final class SettlementPotTests: XCTestCase {
    func testAPaymentToThePot() async {
        let api = FakeAPI()
        let transfer = Transfer(fromMemberId: Fixtures.bas.id, toMemberId: Fixtures.pot.id, amountCents: 1200)
        let model = SettlementViewModel(api: api, group: Fixtures.group, transfer: transfer, today: Fixtures.today)

        XCTAssertEqual(model.members.map(\.name), ["Anna", "Bas", "Chris", "Teamkas"], "Van and Aan include the Teamkas")
        XCTAssertTrue(model.members[3].isPot)
        XCTAssertEqual(model.amountText, "12,00")
        let saved = await model.save()

        XCTAssertTrue(saved)
        XCTAssertEqual(api.lastSettlement, SettlementInput(
            fromMemberId: Fixtures.bas.id, toMemberId: Fixtures.pot.id, amountCents: 1200, date: Fixtures.today
        ))
    }
}
