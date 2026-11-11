import Foundation
import Observation

/// "Boete geven" and "Inleg vragen": a charge of the pot on team members.
@MainActor
@Observable
public final class ChargeViewModel {
    public let type: ChargeType
    /// The team members that can be charged: everyone but the pot.
    public let members: [Party]
    /// The fine list to pick from; empty for a contribution.
    public let fines: [Fine]
    public var description: String
    /// The amount per person, as typed.
    public var amountText = ""
    public var selectedMemberIds: Set<String>
    public var date: CalendarDay
    public private(set) var isSaving = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI
    @ObservationIgnored private let groupId: String

    public init(api: SplitAPI, group: GroupDetail, type: ChargeType, today: CalendarDay = .today()) {
        self.api = api
        self.groupId = group.id
        self.type = type
        self.members = group.parties.filter { !$0.isPot }
        self.date = today
        switch type {
        case .fine:
            // A fine is for someone in particular: nobody is picked yet.
            fines = group.fines
            description = ""
            selectedMemberIds = []
        case .contribution:
            // A contribution is asked of everyone unless told otherwise.
            fines = []
            description = "Inleg \(today.monthName)"
            selectedMemberIds = Set(group.players.map(\.id))
        }
    }

    // MARK: Editing

    public func toggleMember(_ memberId: String) {
        if selectedMemberIds.contains(memberId) {
            selectedMemberIds.remove(memberId)
        } else {
            selectedMemberIds.insert(memberId)
        }
    }

    /// Takes a line of the fine list as the description and the amount.
    public func choose(_ fine: Fine) {
        description = fine.name
        amountText = Money.editingText(fine.amountCents)
    }

    /// Whether the form currently says exactly this line of the fine list.
    public func isChosen(_ fine: Fine) -> Bool {
        description.trimmed == fine.name && amountCents == fine.amountCents
    }

    // MARK: Derived values

    public var amountCents: Int? {
        Money.parse(amountText)
    }

    /// The chosen members in team order, as they are sent.
    private var memberIds: [String] {
        members.map(\.id).filter(selectedMemberIds.contains)
    }

    /// "5 × € 25,00 = € 125,00"; nil while the amount or the members are missing.
    public var totalText: String? {
        guard let amountCents, amountCents > 0, !memberIds.isEmpty else { return nil }
        return Wording.chargeTotal(count: memberIds.count, amountCents: amountCents)
    }

    /// What still keeps the charge from being saved; nil when it is complete.
    public var validationMessage: String? {
        if memberIds.isEmpty { return "Kies minstens één teamlid." }
        if description.trimmed.isEmpty {
            return type == .fine ? "Kies een boete of geef een omschrijving." : "Geef de inleg een omschrijving."
        }
        guard let amountCents, amountCents > 0, amountCents * memberIds.count <= SplitMath.maxAmountCents else {
            return "Vul een geldig bedrag in."
        }
        return nil
    }

    public var canSave: Bool {
        validationMessage == nil && !isSaving
    }

    /// Saves the charge; true when it succeeded.
    public func save() async -> Bool {
        guard canSave, let amountCents else {
            errorMessage = validationMessage
            return false
        }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        let input = ChargeInput(
            type: type, description: description.trimmed, amountCents: amountCents, memberIds: memberIds, date: date
        )
        do {
            _ = try await api.createCharge(groupId: groupId, input: input)
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }
}
