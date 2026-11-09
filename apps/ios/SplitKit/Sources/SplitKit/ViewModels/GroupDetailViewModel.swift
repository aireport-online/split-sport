import Foundation
import Observation

/// "Groep": the expenses and balances of one group.
@MainActor
@Observable
public final class GroupDetailViewModel {
    /// One line of "Uitgaven": "Boodschappen ........ 64,12" with the payer highlighted below it.
    public struct ExpenseRow: Identifiable, Equatable, Sendable {
        public let expense: Expense
        /// The description, or "Bas betaalde Anna" for a payment between members.
        public let title: String
        /// The amount as a till prints it, without the euro sign.
        public let amount: String
        public let payerName: String
        public let payerColor: GroupColor
        /// What follows the payer's name: "betaalde · 2 nov", or "betaling · 2 nov" for a payment.
        public let detail: String
        /// "je krijgt € 6,66" / "je betaalt € 3,33"; nil when it changes nothing for you.
        public let impact: String?

        public var id: String { expense.id }
        /// A payment between members; it carries the "BETAALD" stamp.
        public var isSettlement: Bool { expense.kind == .settlement }
    }

    /// The expenses of one month under their heading ("NOVEMBER 2026").
    public struct ExpenseMonth: Identifiable, Equatable, Sendable {
        public let heading: String
        public let rows: [ExpenseRow]

        public var id: String { heading }
    }

    /// One member in "Saldo".
    public struct BalanceRow: Identifiable, Equatable, Sendable {
        public let id: String
        public let name: String
        public let color: GroupColor
        public let isMe: Bool
        public let hasAccount: Bool
        public let balanceCents: Int
        /// "+ 24,95", "− 21,60" or "0,00".
        public let balance: String
        /// The balance in words for VoiceOver: "krijgt € 24,95".
        public let spokenBalance: String
    }

    /// One suggested payment under "Vereffenen".
    public struct TransferRow: Identifiable, Equatable, Sendable {
        public let transfer: Transfer
        public let payerName: String
        public let payerColor: GroupColor
        public let receiverName: String
        public let receiverColor: GroupColor
        /// The amount as a till prints it, without the euro sign.
        public let amount: String
        /// The whole payment as a sentence for VoiceOver: "Bas betaalt Anna € 3,33".
        public let spoken: String
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

    /// The expenses, newest first, grouped by month.
    public var expenseMonths: [ExpenseMonth] {
        guard let detail else { return [] }
        var months: [(heading: String, rows: [ExpenseRow])] = []
        for expense in expenses {
            let heading = expense.date.monthHeading
            let row = Self.row(for: expense, in: detail)
            if let last = months.indices.last, months[last].heading == heading {
                months[last].rows.append(row)
            } else {
                months.append((heading, [row]))
            }
        }
        return months.map { ExpenseMonth(heading: $0.heading, rows: $0.rows) }
    }

    private static func row(for expense: Expense, in detail: GroupDetail) -> ExpenseRow {
        let payer = detail.memberName(expense.paidById)
        let date = expense.date.shortDutch()
        let isSettlement = expense.kind == .settlement
        let receiver = detail.memberName(expense.shares.first?.memberId ?? "")
        return ExpenseRow(
            expense: expense,
            title: isSettlement ? "\(payer) betaalde \(receiver)" : expense.description,
            amount: Money.plain(expense.amountCents),
            payerName: payer,
            payerColor: detail.color(ofMember: expense.paidById),
            detail: isSettlement ? "betaling · \(date)" : "betaalde · \(date)",
            impact: isSettlement ? nil : Wording.expenseImpact(expense.impactCents(on: detail.myMemberId))
        )
    }

    public var balanceRows: [BalanceRow] {
        guard let detail else { return [] }
        return detail.members.map { member in
            let cents = detail.balanceCents(of: member.id)
            return BalanceRow(
                id: member.id,
                name: member.name,
                color: detail.color(ofMember: member.id),
                isMe: member.id == detail.myMemberId,
                hasAccount: member.hasAccount,
                balanceCents: cents,
                balance: Money.signedPlain(cents),
                spokenBalance: Wording.memberBalance(cents)
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
                payerName: payer,
                payerColor: detail.color(ofMember: transfer.fromMemberId),
                receiverName: receiver,
                receiverColor: detail.color(ofMember: transfer.toMemberId),
                amount: Money.plain(transfer.amountCents),
                spoken: "\(payer) betaalt \(receiver) \(Money.euro(transfer.amountCents))",
                iban: detail.member(transfer.toMemberId)?.iban
            )
        }
    }
}
