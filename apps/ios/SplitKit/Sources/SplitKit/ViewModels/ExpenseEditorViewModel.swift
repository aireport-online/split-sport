import Foundation
import Observation

/// "Uitgave": add or edit an expense, with the four ways to divide it and a
/// live "Per persoon" preview computed by the local `SplitMath.computeShares`.
@MainActor
@Observable
public final class ExpenseEditorViewModel {
    /// One line of the receipt while it is being edited.
    public struct ItemDraft: Identifiable, Equatable, Sendable {
        public let id: UUID
        public var name: String
        public var amountText: String
        public var memberIds: Set<String>

        public init(id: UUID = UUID(), name: String = "", amountText: String = "", memberIds: Set<String>) {
            self.id = id
            self.name = name
            self.amountText = amountText
            self.memberIds = memberIds
        }

        var isBlank: Bool {
            name.trimmed.isEmpty && amountText.trimmed.isEmpty
        }
    }

    /// What one member pays, for the "Per persoon" summary.
    public struct PersonShare: Identifiable, Equatable, Sendable {
        public let id: String
        public let name: String
        public let amountCents: Int

        public var amount: String { Money.format(amountCents) }
    }

    /// The "Per persoon" summary: the shares, or why they cannot be computed yet.
    public enum Preview: Equatable, Sendable {
        case shares([PersonShare])
        case incomplete(String)
    }

    // MARK: Form state

    public let members: [Member]
    public let myMemberId: String
    /// Whether "Bon scannen" is offered (the server supports it).
    public let canScanReceipt: Bool
    public var description: String
    public var amountText: String
    public var date: CalendarDay
    public var paidById: String
    public var mode: SplitMode
    /// "Gelijk": who takes part.
    public var equalMemberIds: Set<String>
    /// "Bedragen": the typed amount per member id.
    public var exactTexts: [String: String]
    /// "Aandelen": the weight per member id; 0 = not in.
    public var weights: [String: Int]
    /// "Per regel": the lines of the receipt.
    public var items: [ItemDraft]

    public private(set) var isSaving = false
    public private(set) var isScanning = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI
    @ObservationIgnored private let groupId: String
    @ObservationIgnored private let expenseId: String?

    /// - Parameter expense: the expense to edit, or nil for a new one.
    public init(
        api: SplitAPI,
        group: GroupDetail,
        expense: Expense? = nil,
        canScanReceipt: Bool,
        today: CalendarDay = .today()
    ) {
        self.api = api
        self.groupId = group.id
        self.expenseId = expense?.id
        self.members = group.members
        self.myMemberId = group.myMemberId
        self.canScanReceipt = canScanReceipt

        let everyone = Set(group.members.map(\.id))
        description = expense?.description ?? ""
        amountText = expense.map { Money.editingText($0.amountCents) } ?? ""
        date = expense?.date ?? today
        paidById = expense?.paidById ?? group.myMemberId
        mode = expense?.splitMode ?? .equal
        equalMemberIds = everyone
        exactTexts = [:]
        weights = Dictionary(uniqueKeysWithValues: group.members.map { ($0.id, 1) })
        items = []

        // Rebuild the split from the stored expense, as docs/api.md prescribes.
        guard let expense else { return }
        switch expense.splitMode {
        case .equal:
            equalMemberIds = Set(expense.shares.map(\.memberId))
        case .exact:
            for share in expense.shares {
                exactTexts[share.memberId] = Money.editingText(share.amountCents)
            }
        case .shares:
            for member in group.members {
                weights[member.id] = expense.shares.first { $0.memberId == member.id }?.weight ?? 0
            }
        case .items:
            items = expense.items.map {
                ItemDraft(name: $0.name, amountText: Money.editingText($0.amountCents), memberIds: Set($0.memberIds))
            }
        }
    }

    public var isEditing: Bool { expenseId != nil }

    // MARK: Editing the split

    public func toggleEqualMember(_ memberId: String) {
        if equalMemberIds.contains(memberId) {
            equalMemberIds.remove(memberId)
        } else {
            equalMemberIds.insert(memberId)
        }
    }

    public func exactText(for memberId: String) -> String {
        exactTexts[memberId] ?? ""
    }

    public func setExactText(_ text: String, for memberId: String) {
        exactTexts[memberId] = text
    }

    public func weight(for memberId: String) -> Int {
        weights[memberId] ?? 0
    }

    public func setWeight(_ weight: Int, for memberId: String) {
        weights[memberId] = min(max(weight, 0), SplitMath.maxWeight)
    }

    /// Adds an empty line that everyone shares until told otherwise.
    public func addItem() {
        items.append(ItemDraft(memberIds: Set(members.map(\.id))))
    }

    public func removeItem(id: UUID) {
        items.removeAll { $0.id == id }
    }

    public func toggleMember(_ memberId: String, inItem itemId: UUID) {
        guard let index = items.firstIndex(where: { $0.id == itemId }) else { return }
        if items[index].memberIds.contains(memberId) {
            items[index].memberIds.remove(memberId)
        } else {
            items[index].memberIds.insert(memberId)
        }
    }

    // MARK: Derived values

    /// The sum of the receipt lines that have a valid amount.
    public var itemsTotalCents: Int {
        items.reduce(0) { $0 + (Money.parse($1.amountText) ?? 0) }
    }

    /// The total of the expense. In "Per regel" an empty amount field means "the sum of the lines".
    public var amountCents: Int? {
        if mode == .items, amountText.trimmed.isEmpty, itemsTotalCents > 0 {
            return itemsTotalCents
        }
        return Money.parse(amountText)
    }

    /// "Bedragen": "Nog € 2,50 te verdelen", or how much was divided too much; nil when it adds up.
    public var exactRemainderText: String? {
        guard let amountCents else { return nil }
        let divided = members.reduce(0) { $0 + (Money.parse(exactText(for: $1.id)) ?? 0) }
        let remaining = amountCents - divided
        if remaining > 0 { return "Nog \(Money.format(remaining)) te verdelen" }
        if remaining < 0 { return "\(Money.format(-remaining)) te veel verdeeld" }
        return nil
    }

    /// "Per regel": "Verschil van € 4,00 wordt naar rato verdeeld." when total and lines differ.
    public var itemsDifferenceText: String? {
        guard let amountCents, itemsTotalCents > 0, amountCents != itemsTotalCents else { return nil }
        return "Verschil van \(Money.format(abs(amountCents - itemsTotalCents))) wordt naar rato verdeeld."
    }

    /// The split as it would be sent now; nil while an amount in it cannot be read.
    public var split: Split? {
        switch mode {
        case .equal:
            return .equal(memberIds: members.map(\.id).filter(equalMemberIds.contains))

        case .exact:
            var amounts: [Split.ExactAmount] = []
            for member in members {
                let text = exactText(for: member.id)
                if text.trimmed.isEmpty { continue }
                guard let cents = Money.parse(text) else { return nil }
                if cents > 0 { amounts.append(Split.ExactAmount(memberId: member.id, amountCents: cents)) }
            }
            return .exact(amounts: amounts)

        case .shares:
            return .shares(weights: members.map { Split.ShareWeight(memberId: $0.id, weight: weight(for: $0.id)) })

        case .items:
            let lines = items.filter { !$0.isBlank }.map { item in
                SplitItem(
                    name: item.name.trimmed,
                    // An unreadable amount becomes 0, which computeShares reports as an invalid line.
                    amountCents: Money.parse(item.amountText) ?? 0,
                    memberIds: members.map(\.id).filter(item.memberIds.contains)
                )
            }
            return .items(lines)
        }
    }

    public var preview: Preview {
        guard let amountCents, amountCents > 0 else {
            let isEmpty = amountText.trimmed.isEmpty
            return .incomplete(isEmpty ? "Vul een bedrag in om de verdeling te zien." : SplitMath.SplitError.invalidAmount.message)
        }
        guard let split else {
            return .incomplete(SplitMath.SplitError.invalidAmount.message)
        }
        do {
            let shares = try SplitMath.computeShares(amountCents: amountCents, split: split)
            return .shares(shares.map { share in
                PersonShare(id: share.memberId, name: memberName(share.memberId), amountCents: share.amountCents)
            })
        } catch let error as SplitMath.SplitError {
            return .incomplete(error.message)
        } catch {
            return .incomplete(SplitMath.SplitError.invalidAmount.message)
        }
    }

    /// What a member pays according to the preview; nil when they are not in or it cannot be computed.
    public func previewAmount(for memberId: String) -> String? {
        guard case .shares(let shares) = preview else { return nil }
        return shares.first { $0.id == memberId }?.amount
    }

    public var canSave: Bool {
        guard case .shares = preview else { return false }
        return !description.trimmed.isEmpty && !isSaving && !isScanning
    }

    public func memberName(_ memberId: String) -> String {
        guard let member = members.first(where: { $0.id == memberId }) else { return "–" }
        return Wording.memberName(member.name, isMe: member.id == myMemberId)
    }

    // MARK: Saving, deleting and scanning

    /// Saves the expense; true when it succeeded.
    public func save() async -> Bool {
        guard canSave, let amountCents, let split else { return false }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        let input = ExpenseInput(
            description: description.trimmed, amountCents: amountCents, date: date, paidById: paidById, split: split
        )
        do {
            if let expenseId {
                _ = try await api.updateExpense(groupId: groupId, expenseId: expenseId, input: input)
            } else {
                _ = try await api.createExpense(groupId: groupId, input: input)
            }
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }

    /// Deletes the expense being edited; true when it succeeded.
    public func delete() async -> Bool {
        guard let expenseId else { return false }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            try await api.deleteExpense(groupId: groupId, expenseId: expenseId)
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }

    /// Sends a photo of a receipt (JPEG, already scaled down) and fills the form from what the server read.
    public func scanReceipt(jpegData: Data) async {
        isScanning = true
        errorMessage = nil
        defer { isScanning = false }
        do {
            apply(try await api.scanReceipt(jpegData: jpegData))
        } catch {
            errorMessage = error.userMessage
        }
    }

    /// Fills the lines, the total, the date and the description from a scanned receipt.
    /// Every line starts shared by everyone; a description that was already typed is kept.
    func apply(_ scan: ReceiptScan) {
        let everyone = Set(members.map(\.id))
        mode = .items
        items = scan.items.map {
            ItemDraft(name: $0.name, amountText: Money.editingText($0.amountCents), memberIds: everyone)
        }
        let total = scan.totalCents ?? scan.items.reduce(0) { $0 + $1.amountCents }
        amountText = Money.editingText(total)
        if let scanned = scan.date.flatMap(CalendarDay.init(isoString:)) {
            date = scanned
        }
        if let merchant = scan.merchant, description.trimmed.isEmpty {
            description = merchant
        }
    }
}
