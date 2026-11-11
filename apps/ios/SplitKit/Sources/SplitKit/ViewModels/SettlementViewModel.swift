import Foundation
import Observation

/// "Betaling": records that a member paid another member, or the Teamkas, or the other way round.
@MainActor
@Observable
public final class SettlementViewModel {
    /// Who can pay or be paid: the team members and the Teamkas.
    public let members: [Party]
    public var fromMemberId: String
    public var toMemberId: String
    public var amountText: String
    public var date: CalendarDay
    public private(set) var isSaving = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI
    @ObservationIgnored private let groupId: String

    /// - Parameter transfer: a suggested payment to prefill; without one the payment is from yourself.
    public init(api: SplitAPI, group: GroupDetail, transfer: Transfer?, today: CalendarDay = .today()) {
        self.api = api
        self.groupId = group.id
        self.members = group.parties
        self.date = today
        if let transfer {
            fromMemberId = transfer.fromMemberId
            toMemberId = transfer.toMemberId
            amountText = Money.editingText(transfer.amountCents)
        } else {
            fromMemberId = group.myMemberId
            toMemberId = group.members.first { $0.id != group.myMemberId }?.id ?? group.myMemberId
            amountText = ""
        }
    }

    public var amountCents: Int? {
        Money.parse(amountText)
    }

    /// What still keeps the payment from being saved; nil when it is complete.
    public var validationMessage: String? {
        if fromMemberId == toMemberId { return "Kies twee verschillende leden." }
        guard let amountCents, amountCents > 0, amountCents <= SplitMath.maxAmountCents else {
            return "Vul een geldig bedrag in."
        }
        return nil
    }

    public var canSave: Bool {
        validationMessage == nil && !isSaving
    }

    /// Saves the payment; true when it succeeded.
    public func save() async -> Bool {
        guard canSave, let amountCents else {
            errorMessage = validationMessage
            return false
        }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            let input = SettlementInput(fromMemberId: fromMemberId, toMemberId: toMemberId, amountCents: amountCents, date: date)
            _ = try await api.createSettlement(groupId: groupId, input: input)
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }
}
