import Foundation
import Observation

/// "Team": everything that happened, the team pot and the balances of one team.
@MainActor
@Observable
public final class GroupDetailViewModel {
    /// The rubber stamp on an entry that is not an ordinary expense.
    public enum EntryStamp: Sendable {
        /// "BETAALD": a payment between members or with the pot.
        case paid
        /// "BOETE".
        case fine
        /// "INLEG".
        case contribution
    }

    /// One line of "Uitgaven": "Wedstrijdballen ........ 47,50" with a second line below it.
    public struct ExpenseRow: Identifiable, Equatable, Sendable {
        public let expense: Expense
        /// The description, or "Bas betaalde Teamkas" for a payment.
        public let title: String
        /// The total as a till prints it, without the euro sign.
        public let amount: String
        /// Who the second line starts with: the payer, or the fined members; empty for a contribution.
        public let parties: [Party]
        /// How many fined members are not shown because the line would get too long.
        public let hiddenPartyCount: Int
        /// What follows them: "betaalde · 2 nov", "betaling · 2 nov", "2 nov" or "5 × 25,00 · 2 nov".
        public let detail: String
        /// "je krijgt € 6,66" / "je betaalt € 3,33" on an expense; nil when it changes nothing for you.
        public let impact: String?
        public let stamp: EntryStamp?

        public var id: String { expense.id }
        /// Only ordinary expenses can be changed; payments, fines and contributions can only be removed.
        public var isEditable: Bool { expense.kind == .expense }
    }

    /// The entries of one month under their heading ("NOVEMBER 2026").
    public struct ExpenseMonth: Identifiable, Equatable, Sendable {
        public let heading: String
        public let rows: [ExpenseRow]

        public var id: String { heading }
    }

    /// The "Teamkas" receipt: the cash, what is still open and who keeps it.
    public struct PotSummary: Equatable, Sendable {
        /// The kasstand as the large figure: "€ 47,50".
        public let cash: String
        /// "Nog te ontvangen": what members still owe the pot, as a receipt line without the euro sign.
        public let toReceive: String
        /// "Nog uit te betalen": what the pot owes members who advanced something.
        public let toPay: String
        /// The penningmeester; nil when the team has none.
        public let treasurer: Party?
        /// The treasurer's IBAN as stored, for the clipboard; nil when unknown.
        public let treasurerIBAN: String?

        /// The IBAN in groups of four, for reading.
        public var treasurerIBANText: String? { treasurerIBAN.map(Wording.groupedIBAN) }
    }

    /// One team member in "Saldo": what they have with the other members.
    public struct BalanceRow: Identifiable, Equatable, Sendable {
        public let party: Party
        public let isMe: Bool
        public let hasAccount: Bool
        public let balanceCents: Int
        /// "+ 24,95", "− 21,60" or "0,00".
        public let balance: String
        /// The balance in words for VoiceOver: "krijgt € 24,95".
        public let spokenBalance: String

        public var id: String { party.id }
    }

    /// One suggested payment, with the pot ("Teamkas") or between members ("Vereffenen").
    public struct TransferRow: Identifiable, Equatable, Sendable {
        public let transfer: Transfer
        public let payer: Party
        public let receiver: Party
        /// The amount as a till prints it, without the euro sign.
        public let amount: String
        /// The whole payment as a sentence for VoiceOver: "Bas betaalt Anna € 3,33".
        public let spoken: String
        /// The IBAN to pay to, for the clipboard: the receiver's, or the treasurer's when paying the pot.
        public let iban: String?

        public var id: String { "\(transfer.fromMemberId)>\(transfer.toMemberId)" }

        /// The IBAN in groups of four, for reading.
        public var ibanText: String? { iban.map(Wording.groupedIBAN) }
    }

    /// One line of the "Boetekoning" ranking.
    public struct FineRankRow: Identifiable, Equatable, Sendable {
        public let party: Party
        /// "3×".
        public let count: String
        /// The total of their fines, without the euro sign.
        public let total: String
        /// The first of the ranking carries the "KONING" stamp.
        public let isKing: Bool
        /// The line as a sentence for VoiceOver: "Bas, 3 boetes, € 9,00".
        public let spoken: String

        public var id: String { party.id }
    }

    public let groupId: String
    /// Known from the teams list before the detail has loaded.
    public private(set) var name: String
    public private(set) var color: GroupColor
    public private(set) var detail: GroupDetail?
    public private(set) var expenses: [Expense] = []
    public private(set) var isLoading = false
    /// Why loading failed. With `detail` still nil there is nothing else to show.
    public var errorMessage: String?
    /// Why an action (such as removing an entry) failed.
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

    /// Takes over a team the server returned after a change (for example from the settings screen).
    public func apply(_ detail: GroupDetail) {
        self.detail = detail
        name = detail.name
        color = detail.color
    }

    /// Removes an expense, a payment, a fine or a contribution and reloads the balances.
    public func delete(_ expense: Expense) async {
        do {
            try await api.deleteExpense(groupId: groupId, expenseId: expense.id)
            expenses.removeAll { $0.id == expense.id }
        } catch {
            actionError = error.userMessage
        }
        await load()
    }

    // MARK: Uitgaven

    /// Everything that happened, newest first, grouped by month.
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

    /// How many fined members a line names before it says "+2".
    private static let shownPartyLimit = 3

    private static func row(for expense: Expense, in detail: GroupDetail) -> ExpenseRow {
        let date = expense.date.shortDutch()
        let amount = Money.plain(expense.amountCents)
        let payer = detail.party(expense.paidById)

        switch expense.kind {
        case .expense:
            return ExpenseRow(
                expense: expense, title: expense.description, amount: amount,
                parties: [payer], hiddenPartyCount: 0, detail: "betaalde · \(date)",
                impact: Wording.expenseImpact(expense.impactCents(on: detail.myMemberId)), stamp: nil
            )
        case .settlement:
            let receiver = detail.memberName(expense.shares.first?.memberId ?? "")
            return ExpenseRow(
                expense: expense, title: "\(payer.name) betaalde \(receiver)", amount: amount,
                parties: [payer], hiddenPartyCount: 0, detail: "betaling · \(date)", impact: nil, stamp: .paid
            )
        case .fine:
            let fined = expense.shares.map { detail.party($0.memberId) }
            return ExpenseRow(
                expense: expense, title: expense.description, amount: amount,
                parties: Array(fined.prefix(shownPartyLimit)),
                hiddenPartyCount: max(0, fined.count - shownPartyLimit),
                detail: date, impact: nil, stamp: .fine
            )
        case .contribution:
            let each = Money.plain(expense.shares.first?.amountCents ?? 0)
            return ExpenseRow(
                expense: expense, title: expense.description, amount: amount,
                parties: [], hiddenPartyCount: 0, detail: "\(expense.shares.count) × \(each) · \(date)",
                impact: nil, stamp: .contribution
            )
        }
    }

    // MARK: Teamkas

    public var potSummary: PotSummary? {
        guard let detail else { return nil }
        let treasurer = detail.treasurerMemberId.flatMap(detail.member)
        return PotSummary(
            cash: Money.cash(detail.pot.cashCents),
            toReceive: Money.plain(detail.pot.toReceiveCents),
            toPay: Money.plain(detail.pot.toPayCents),
            treasurer: treasurer.map { detail.party($0.id) },
            treasurerIBAN: treasurer?.iban
        )
    }

    /// What every member has with the pot: "Bas → Teamkas 12,00" or "Teamkas → Chris 40,00".
    public var potTransferRows: [TransferRow] {
        transferRows { $0.payer.isPot || $0.receiver.isPot }
    }

    // MARK: Saldo

    /// Every team member with what they have with the other members.
    public var balanceRows: [BalanceRow] {
        guard let detail else { return [] }
        return detail.parties.filter { !$0.isPot }.map { party in
            let cents = detail.balance(of: party.id)?.peerCents ?? 0
            return BalanceRow(
                party: party,
                isMe: party.id == detail.myMemberId,
                hasAccount: detail.member(party.id)?.hasAccount ?? false,
                balanceCents: cents,
                balance: Money.signedPlain(cents),
                spokenBalance: Wording.memberBalance(cents)
            )
        }
    }

    /// "Vereffenen": the suggested payments between members.
    public var peerTransferRows: [TransferRow] {
        transferRows { !$0.payer.isPot && !$0.receiver.isPot }
    }

    /// "Boetekoning": most fines first; empty while nobody has a fine.
    public var fineRanking: [FineRankRow] {
        guard let detail else { return [] }
        return detail.fineStats.enumerated().map { index, stat in
            let party = detail.party(stat.memberId)
            let fines = stat.count == 1 ? "1 boete" : "\(stat.count) boetes"
            return FineRankRow(
                party: party,
                count: "\(stat.count)×",
                total: Money.plain(stat.totalCents),
                isKing: index == 0,
                spoken: "\(party.name), \(fines), \(Money.euro(stat.totalCents))"
            )
        }
    }

    private func transferRows(where isIncluded: (TransferRow) -> Bool) -> [TransferRow] {
        guard let detail else { return [] }
        let treasurerIBAN = detail.treasurerMemberId.flatMap(detail.member)?.iban
        return detail.transfers.map { transfer in
            let payer = detail.party(transfer.fromMemberId)
            let receiver = detail.party(transfer.toMemberId)
            return TransferRow(
                transfer: transfer,
                payer: payer,
                receiver: receiver,
                amount: Money.plain(transfer.amountCents),
                spoken: "\(payer.name) betaalt \(receiver.name) \(Money.euro(transfer.amountCents))",
                // Paying the pot is, in real life, a transfer to the penningmeester.
                iban: receiver.isPot ? treasurerIBAN : detail.member(receiver.id)?.iban
            )
        }
        .filter(isIncluded)
    }
}
