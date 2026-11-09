import XCTest
@testable import SplitKit

@MainActor
final class ExpenseEditorViewModelTests: XCTestCase {
    private let api = FakeAPI()
    private let anna = Fixtures.anna.id
    private let bas = Fixtures.bas.id
    private let chris = Fixtures.chris.id

    private func makeModel(editing expense: Expense? = nil, canScan: Bool = true) -> ExpenseEditorViewModel {
        ExpenseEditorViewModel(api: api, group: Fixtures.group, expense: expense, canScanReceipt: canScan, today: Fixtures.today)
    }

    private func amounts(_ model: ExpenseEditorViewModel) -> [String: Int] {
        guard case .shares(let shares) = model.preview else { return [:] }
        return Dictionary(uniqueKeysWithValues: shares.map { ($0.id, $0.amountCents) })
    }

    private func message(_ model: ExpenseEditorViewModel) -> String? {
        guard case .incomplete(let message) = model.preview else { return nil }
        return message
    }

    // MARK: New expense

    func testANewExpenseStartsEqualPaidByYouToday() {
        let model = makeModel()
        XCTAssertFalse(model.isEditing)
        XCTAssertEqual(model.mode, .equal)
        XCTAssertEqual(model.paidById, anna)
        XCTAssertEqual(model.date, Fixtures.today)
        XCTAssertEqual(model.equalMemberIds, [anna, bas, chris])
        XCTAssertEqual(message(model), "Vul een bedrag in om de verdeling te zien.")
        XCTAssertFalse(model.canSave)
    }

    func testEqualPreviewFollowsTheAmountAndTheTicks() {
        let model = makeModel()
        model.amountText = "10"
        XCTAssertEqual(amounts(model), [anna: 334, bas: 333, chris: 333])
        XCTAssertEqual(model.previewAmount(for: anna), "€\u{00A0}3,34")

        model.toggleEqualMember(bas)
        XCTAssertEqual(amounts(model), [anna: 500, chris: 500])
        XCTAssertNil(model.previewAmount(for: bas))

        model.toggleEqualMember(anna)
        model.toggleEqualMember(chris)
        XCTAssertEqual(message(model), "Kies minstens één deelnemer.")

        model.toggleEqualMember(bas)
        XCTAssertEqual(amounts(model), [bas: 1000])
    }

    func testPreviewNamesMarkYourself() {
        let model = makeModel()
        model.amountText = "9"
        guard case .shares(let shares) = model.preview else { return XCTFail("Expected shares") }
        XCTAssertEqual(shares.map(\.name), ["Anna (jij)", "Bas", "Chris"])
        XCTAssertEqual(shares.map(\.amount), Array(repeating: "€\u{00A0}3,00", count: 3))
    }

    func testAnUnreadableAmountIsReported() {
        let model = makeModel()
        model.amountText = "tien"
        XCTAssertEqual(message(model), "Vul een geldig bedrag in.")
        model.amountText = "0"
        XCTAssertEqual(message(model), "Vul een geldig bedrag in.")
        model.amountText = "1.000.000"
        XCTAssertEqual(message(model), "Vul een geldig bedrag in.")
    }

    func testSavingNeedsADescription() async {
        let model = makeModel()
        model.amountText = "12,50"
        XCTAssertFalse(model.canSave)
        let saved = await model.save()
        XCTAssertFalse(saved)
        XCTAssertEqual(api.calls, [])

        model.description = "  Boodschappen "
        XCTAssertTrue(model.canSave)
    }

    func testSavesANewEqualExpense() async {
        let model = makeModel()
        model.description = " Boodschappen "
        model.amountText = "12,50"
        model.paidById = bas
        model.toggleEqualMember(chris)
        let saved = await model.save()

        XCTAssertTrue(saved)
        XCTAssertEqual(api.calls, ["createExpense"])
        XCTAssertEqual(api.lastExpenseInput, ExpenseInput(
            description: "Boodschappen", amountCents: 1250, date: Fixtures.today, paidById: bas,
            split: .equal(memberIds: [anna, bas])
        ))
    }

    // MARK: Bedragen

    func testExactShowsWhatIsLeftToDivide() {
        let model = makeModel()
        model.mode = .exact
        model.amountText = "10"
        XCTAssertEqual(model.exactRemainderText, "Nog €\u{00A0}10,00 te verdelen")
        XCTAssertEqual(message(model), "Kies minstens één deelnemer.")

        model.setExactText("7,50", for: anna)
        XCTAssertEqual(model.exactRemainderText, "Nog €\u{00A0}2,50 te verdelen")
        XCTAssertEqual(message(model), "De bedragen tellen niet op tot het totaal.")
        XCTAssertFalse(model.canSave)

        model.setExactText("2.5", for: chris)
        XCTAssertNil(model.exactRemainderText)
        XCTAssertEqual(amounts(model), [anna: 750, chris: 250])
        XCTAssertEqual(model.split, .exact(amounts: [
            .init(memberId: anna, amountCents: 750), .init(memberId: chris, amountCents: 250),
        ]))

        model.setExactText("3", for: chris)
        XCTAssertEqual(model.exactRemainderText, "€\u{00A0}0,50 te veel verdeeld")
    }

    func testExactWithAnUnreadableAmountCannotBeSaved() {
        let model = makeModel()
        model.mode = .exact
        model.description = "Taxi"
        model.amountText = "10"
        model.setExactText("10", for: anna)
        XCTAssertTrue(model.canSave)
        model.setExactText("x", for: bas)
        XCTAssertNil(model.split)
        XCTAssertEqual(message(model), "Vul een geldig bedrag in.")
        XCTAssertFalse(model.canSave)
    }

    // MARK: Aandelen

    func testSharesUseTheSteppers() {
        let model = makeModel()
        model.mode = .shares
        model.amountText = "90"
        XCTAssertEqual(amounts(model), [anna: 3000, bas: 3000, chris: 3000])

        model.setWeight(2, for: anna)
        model.setWeight(0, for: chris)
        XCTAssertEqual(amounts(model), [anna: 6000, bas: 3000])
        XCTAssertEqual(model.split, .shares(weights: [
            .init(memberId: anna, weight: 2), .init(memberId: bas, weight: 1), .init(memberId: chris, weight: 0),
        ]))

        model.setWeight(-3, for: bas)
        XCTAssertEqual(model.weight(for: bas), 0, "A weight never goes below zero")
        model.setWeight(0, for: anna)
        XCTAssertEqual(message(model), "Kies minstens één deelnemer.")
    }

    // MARK: Per regel

    func testItemsDivideEveryLineOverItsMembers() {
        let model = makeModel()
        model.mode = .items
        model.addItem()
        model.addItem()
        XCTAssertEqual(model.items[0].memberIds, [anna, bas, chris], "A new line starts shared by everyone")

        model.items[0].name = "Pizza"
        model.items[0].amountText = "11"
        model.items[1].name = "Wijn"
        model.items[1].amountText = "9,00"
        model.toggleMember(bas, inItem: model.items[0].id)
        model.toggleMember(chris, inItem: model.items[0].id)

        XCTAssertEqual(model.itemsTotalCents, 2000)
        XCTAssertEqual(model.amountCents, 2000, "An empty total follows the lines")
        XCTAssertNil(model.itemsDifferenceText)
        XCTAssertEqual(amounts(model), [anna: 1400, bas: 300, chris: 300])
    }

    func testItemsSpreadTheDifferenceWithTheTotal() {
        let model = makeModel()
        model.mode = .items
        model.amountText = "24"
        model.items = [
            .init(name: "Pizza", amountText: "15", memberIds: [anna]),
            .init(name: "Pasta", amountText: "5", memberIds: [bas]),
        ]
        XCTAssertEqual(model.itemsDifferenceText, "Verschil van €\u{00A0}4,00 wordt naar rato verdeeld.")
        XCTAssertEqual(amounts(model), [anna: 1800, bas: 600])

        model.amountText = "18"
        XCTAssertEqual(model.itemsDifferenceText, "Verschil van €\u{00A0}2,00 wordt naar rato verdeeld.")
        XCTAssertEqual(amounts(model), [anna: 1350, bas: 450])
    }

    func testItemsReportUnassignedAndUnpricedLines() {
        let model = makeModel()
        model.mode = .items
        model.amountText = "10"
        XCTAssertEqual(message(model), "Kies minstens één deelnemer.")

        model.items = [.init(name: "Cola", amountText: "10", memberIds: [])]
        XCTAssertEqual(message(model), "Wijs elke regel van de bon aan iemand toe.")

        model.items = [.init(name: "Cola", amountText: "", memberIds: [anna])]
        XCTAssertEqual(message(model), "Elke regel van de bon heeft een bedrag nodig.")

        model.items = [.init(name: "Cola", amountText: "10", memberIds: [anna]), .init(memberIds: [anna, bas])]
        XCTAssertEqual(amounts(model), [anna: 1000], "A completely blank line is ignored")
    }

    func testRemovingAndTogglingLines() {
        let model = makeModel()
        model.addItem()
        model.addItem()
        let first = model.items[0].id
        model.toggleMember(anna, inItem: first)
        XCTAssertEqual(model.items[0].memberIds, [bas, chris])
        model.toggleMember(anna, inItem: first)
        XCTAssertEqual(model.items[0].memberIds, [anna, bas, chris])
        model.removeItem(id: first)
        XCTAssertEqual(model.items.count, 1)
    }

    func testItemsAreSentInMemberOrder() async {
        let model = makeModel()
        model.description = "Uit eten"
        model.mode = .items
        model.amountText = "25"
        model.items = [
            .init(name: " Pizza ", amountText: "15", memberIds: [chris, anna]),
            .init(name: "", amountText: "5", memberIds: [bas]),
        ]
        let saved = await model.save()
        XCTAssertTrue(saved)
        XCTAssertEqual(api.lastExpenseInput?.amountCents, 2500)
        XCTAssertEqual(api.lastExpenseInput?.split, .items([
            SplitItem(name: "Pizza", amountCents: 1500, memberIds: [anna, chris]),
            SplitItem(name: "", amountCents: 500, memberIds: [bas]),
        ]))
    }

    // MARK: Editing

    func testEditingRebuildsAnEqualSplit() {
        let expense = Fixtures.expense(paidById: bas, shares: [
            ExpenseShare(memberId: anna, amountCents: 1500, weight: nil),
            ExpenseShare(memberId: bas, amountCents: 1500, weight: nil),
        ])
        let model = makeModel(editing: expense)

        XCTAssertTrue(model.isEditing)
        XCTAssertEqual(model.description, "Boodschappen")
        XCTAssertEqual(model.amountText, "30,00")
        XCTAssertEqual(model.date, CalendarDay(year: 2026, month: 11, day: 2))
        XCTAssertEqual(model.paidById, bas)
        XCTAssertEqual(model.equalMemberIds, [anna, bas])
        XCTAssertEqual(model.split, .equal(memberIds: [anna, bas]))
        XCTAssertTrue(model.canSave)
    }

    func testEditingRebuildsExactAmounts() {
        let expense = Fixtures.expense(splitMode: .exact, shares: [
            ExpenseShare(memberId: anna, amountCents: 1250, weight: nil),
            ExpenseShare(memberId: chris, amountCents: 1750, weight: nil),
        ])
        let model = makeModel(editing: expense)
        XCTAssertEqual(model.mode, .exact)
        XCTAssertEqual(model.exactText(for: anna), "12,50")
        XCTAssertEqual(model.exactText(for: bas), "")
        XCTAssertEqual(amounts(model), [anna: 1250, chris: 1750])
    }

    func testEditingRebuildsWeights() {
        let expense = Fixtures.expense(splitMode: .shares, shares: [
            ExpenseShare(memberId: anna, amountCents: 2000, weight: 2),
            ExpenseShare(memberId: chris, amountCents: 1000, weight: 1),
        ])
        let model = makeModel(editing: expense)
        XCTAssertEqual(model.mode, .shares)
        XCTAssertEqual([anna, bas, chris].map(model.weight(for:)), [2, 0, 1])
        XCTAssertEqual(amounts(model), [anna: 2000, chris: 1000])
    }

    func testEditingRebuildsReceiptLines() {
        let expense = Fixtures.expense(
            amountCents: 2300, splitMode: .items,
            shares: [
                ExpenseShare(memberId: anna, amountCents: 1265, weight: nil),
                ExpenseShare(memberId: bas, amountCents: 1035, weight: nil),
            ],
            items: [
                SplitItem(name: "Margherita", amountCents: 1100, memberIds: [anna]),
                SplitItem(name: "Funghi", amountCents: 900, memberIds: [bas]),
            ]
        )
        let model = makeModel(editing: expense)
        XCTAssertEqual(model.mode, .items)
        XCTAssertEqual(model.items.map(\.name), ["Margherita", "Funghi"])
        XCTAssertEqual(model.items.map(\.amountText), ["11,00", "9,00"])
        XCTAssertEqual(model.itemsDifferenceText, "Verschil van €\u{00A0}3,00 wordt naar rato verdeeld.")
        XCTAssertEqual(amounts(model), [anna: 1265, bas: 1035], "The local preview matches what the server stored")
    }

    func testSavingAnEditUpdatesTheExpense() async {
        let model = makeModel(editing: Fixtures.expense(id: "e9"))
        model.amountText = "45"
        let saved = await model.save()
        XCTAssertTrue(saved)
        XCTAssertEqual(api.calls, ["updateExpense"])
        XCTAssertEqual(api.lastExpenseId, "e9")
        XCTAssertEqual(api.lastExpenseInput?.amountCents, 4500)
    }

    func testDeletingAnEdit() async {
        let model = makeModel(editing: Fixtures.expense(id: "e9"))
        let deleted = await model.delete()
        XCTAssertTrue(deleted)
        XCTAssertEqual(api.lastExpenseId, "e9")

        let newModel = makeModel()
        let nothing = await newModel.delete()
        XCTAssertFalse(nothing, "A new expense has nothing to delete")
    }

    func testShowsWhySavingFailed() async {
        api.failure = APIError.server(status: 400, code: "unknown_member", message: "Een van de leden hoort niet bij deze groep.")
        let model = makeModel(editing: Fixtures.expense())
        let saved = await model.save()
        XCTAssertFalse(saved)
        XCTAssertEqual(model.errorMessage, "Een van de leden hoort niet bij deze groep.")
        XCTAssertFalse(model.isSaving)
    }

    // MARK: Receipt scanning

    func testAScanFillsLinesTotalDateAndDescription() async {
        api.scanResult = ReceiptScan(merchant: "Café De Zon", date: "2026-11-01", totalCents: 2350, items: [
            .init(name: "2× Bier", amountCents: 900), .init(name: "Bitterballen", amountCents: 1250),
        ])
        let model = makeModel()
        await model.scanReceipt(jpegData: Data([1, 2, 3]))

        XCTAssertEqual(api.lastScanData, Data([1, 2, 3]))
        XCTAssertEqual(model.mode, .items)
        XCTAssertEqual(model.items.map(\.name), ["2× Bier", "Bitterballen"])
        XCTAssertEqual(model.items.map(\.amountText), ["9,00", "12,50"])
        XCTAssertEqual(model.items.map(\.memberIds), [[anna, bas, chris], [anna, bas, chris]])
        XCTAssertEqual(model.amountText, "23,50")
        XCTAssertEqual(model.date, CalendarDay(year: 2026, month: 11, day: 1))
        XCTAssertEqual(model.description, "Café De Zon")
        XCTAssertEqual(model.itemsDifferenceText, "Verschil van €\u{00A0}2,00 wordt naar rato verdeeld.")
        XCTAssertTrue(model.canSave)
        XCTAssertFalse(model.isScanning)
    }

    func testAScanKeepsWhatItCouldNotRead() async {
        api.scanResult = ReceiptScan(merchant: nil, date: "gisteren", totalCents: nil, items: [.init(name: "Brood", amountCents: 250)])
        let model = makeModel()
        model.description = "Bakker"
        await model.scanReceipt(jpegData: Data())

        XCTAssertEqual(model.description, "Bakker", "A typed description is not overwritten")
        XCTAssertEqual(model.date, Fixtures.today)
        XCTAssertEqual(model.amountText, "2,50", "Without a total the lines add up to it")
    }

    func testAFailedScanShowsTheServerMessage() async {
        api.failure = APIError.server(status: 422, code: "no_receipt", message: "We zien geen bon op deze foto. Probeer het opnieuw of vul de bon zelf in.")
        let model = makeModel()
        await model.scanReceipt(jpegData: Data())
        XCTAssertEqual(model.errorMessage, "We zien geen bon op deze foto. Probeer het opnieuw of vul de bon zelf in.")
        XCTAssertEqual(model.mode, .equal)
        XCTAssertTrue(model.items.isEmpty)
    }

    func testScanButtonFollowsTheServerConfig() {
        XCTAssertTrue(makeModel(canScan: true).canScanReceipt)
        XCTAssertFalse(makeModel(canScan: false).canScanReceipt)
    }
}
