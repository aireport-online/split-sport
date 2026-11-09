import Foundation
import Observation

/// "Groep": the expenses and balances of one group.
@MainActor
@Observable
public final class GroupDetailViewModel {
    /// One line of "Uitgaven".
    public struct ExpenseRow: Identifiable, Equatable, Sendable {
        public let expense: Expense
        /// The description, or "Bas betaalde Anna" for a payment between members.
        public let title: String
        /// "Anna betaalde · 2 nov", or just the date for a payment.
        public let subtitle: String
        public let amount: String
        /// "je krijgt € 6,66" / "je betaalt € 3,33"; nil when it changes nothing for you.
        public let impact: String?
        public let impactSign: Wording.BalanceSign

        public var id: String { expense.id }
        public var isSettlement: Bool { expense.kind == .settlement }
    }

    /// One member in "Saldo".
    public struct BalanceRow: Identifiable, Equatable, Sendable {
        public let id: String
        /// "Anna (jij)" for yourself.
        public let name: String
        public let initials: String
        public let tint: GroupColor
        public let hasAccount: Bool
        /// "krijgt € 12,50", "betaalt € 8,00" or "quitte".
        public let balance: String
        public let sign: Wording.BalanceSign
    }

    /// One suggested payment under "Vereffenen".
    public struct TransferRow: Identifiable, Equatable, Sendable {
        public let transfer: Transfer
        /// "Bas betaalt Anna € 3,33".
        public let text: String
        public let receiverName: String
        /// The receiver's IBAN as stored, for the clipboard; nil when they have none.
        public let iban: String?

        public var id: String { "\(transfer.fromMemberId)>\(transfer.toMemberId)" }

        /// The IBAN in groups of four, for reading.
        public var ibanText: String? { iban.map(Wording.groupedIBAN) }
    }

    public let groupId: String
    /// Known from the groups list before the detail has loaded.
    public private(set) var name: String
    public private(set) var color: GroupColor
    public private(set) var detail: GroupDetail?
    public private(set) var expenses: [Expense] = []
    public private(set) var isLoading = false
    /// Why loading failed. With `detail` still nil there is nothing else to show.
    public var errorMessage: String?
    /// Why an action (such as deleting an expense) failed.
    public var actionError: String?

    @ObservationIgnored private let api: SplitAPI

    public init(api: SplitAPI, groupId: String, name: String, color: GroupColor) {
        self.api = api
        self.groupId = groupId
        self.name = name
        self.color = color
    }

    // MARK: Loading and mutations

    public func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let detail = api.group(id: groupId)
            async let expenses = api.expenses(groupId: groupId)
            let (loadedDetail, loadedExpenses) = try await (detail, expenses)
            apply(loadedDetail)
            self.expenses = loadedExpenses
            errorMessage = nil
        } catch {
            if let message = error.userMessage { errorMessage = message }
        }
    }

    /// Takes over a group the server returned after a change (for example from the settings screen).
    public func apply(_ detail: GroupDetail) {
        self.detail = detail
        name = detail.name
        color = detail.color
    }

    /// Deletes an expense or a payment and reloads the balances.
    public func delete(_ expense: Expense) async {
        do {
            try await api.deleteExpense(groupId: groupId, expenseId: expense.id)
            expenses.removeAll { $0.id == expense.id }
        } catch {
            actionError = error.userMessage
        }
        await load()
    }

    // MARK: Presentation

    public var expenseRows: [ExpenseRow] {
        guard let detail else { return [] }
        return expenses.map { expense in
            let date = expense.date.shortDutch()
            let payer = detail.memberName(expense.paidById)
            switch expense.kind {
            case .settlement:
                let receiver = detail.memberName(expense.shares.first?.memberId ?? "")
                return ExpenseRow(
                    expense: expense, title: "\(payer) betaalde \(receiver)", subtitle: date,
                    amount: Money.format(expense.amountCents), impact: nil, impactSign: .zero
                )
            case .expense:
                let impact = expense.impactCents(on: detail.myMemberId)
                return ExpenseRow(
                    expense: expense, title: expense.description, subtitle: "\(payer) betaalde · \(date)",
                    amount: Money.format(expense.amountCents), impact: Wording.expenseImpact(impact),
                    impactSign: Wording.BalanceSign(cents: impact ?? 0)
                )
            }
        }
    }

    public var balanceRows: [BalanceRow] {
        guard let detail else { return [] }
        return detail.members.enumerated().map { index, member in
            let cents = detail.balanceCents(of: member.id)
            return BalanceRow(
                id: member.id,
                name: Wording.memberName(member.name, isMe: member.id == detail.myMemberId),
                initials: Initials.of(member.name),
                tint: GroupColor.palette(at: index),
                hasAccount: member.hasAccount,
                balance: Wording.memberBalance(cents),
                sign: Wording.BalanceSign(cents: cents)
            )
        }
    }

    public var transferRows: [TransferRow] {
        guard let detail else { return [] }
        return detail.transfers.map { transfer in
            let payer = detail.memberName(transfer.fromMemberId)
            let receiver = detail.memberName(transfer.toMemberId)
            return TransferRow(
                transfer: transfer,
                text: "\(payer) betaalt \(receiver) \(Money.format(transfer.amountCents))",
                receiverName: receiver,
                iban: detail.member(transfer.toMemberId)?.iban
            )
        }
    }
}
