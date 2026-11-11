import SplitKit
import SwiftUI

/// The "Uitgaven" part of a team: one receipt with everything that happened
/// per month, newest first. Tapping a line opens it.
@MainActor
struct ExpensesReceipt: View {
    let months: [GroupDetailViewModel.ExpenseMonth]
    let onSelect: (GroupDetailViewModel.ExpenseRow) -> Void

    var body: some View {
        Receipt {
            if months.isEmpty {
                ReceiptTitle(text: "Nog niets genoteerd")
                Text("Vraag de eerste inleg of voeg een uitgave toe.")
                    .foregroundStyle(Theme.mutedInk)
            } else {
                ForEach(Array(months.enumerated()), id: \.element.id) { index, month in
                    if index > 0 {
                        Perforation()
                    }
                    Text(month.heading)
                        .font(.tillSmall)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(month.rows) { row in
                        Button {
                            onSelect(row)
                        } label: {
                            ExpenseLine(row: row)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

/// "Wedstrijdballen ........ 47,50", and below it who it is about (the payer,
/// or the fined members), the date and what it means for you or a stamp.
@MainActor
private struct ExpenseLine: View {
    let row: GroupDetailViewModel.ExpenseRow

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            LeaderRow(amount: row.amount) {
                Text(row.title)
                    .lineLimit(2)
            }
            // Side by side when it fits; below each other with large type or long names.
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    who
                    Spacer(minLength: 8)
                    outcome
                }
                VStack(alignment: .leading, spacing: 4) {
                    who
                    outcome
                }
            }
            .font(.tillSmall)
            .foregroundStyle(Theme.mutedInk)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var who: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            ForEach(row.parties) { party in
                PartyName(party: party)
            }
            if row.hiddenPartyCount > 0 {
                Text("+\(row.hiddenPartyCount)")
            }
            Text(row.detail)
                .lineLimit(1)
        }
    }

    @ViewBuilder
    private var outcome: some View {
        if let stamp = row.stamp {
            Stamp(kind: stampKind(stamp))
        } else if let impact = row.impact {
            Text(impact)
                .lineLimit(1)
        }
    }

    private func stampKind(_ stamp: GroupDetailViewModel.EntryStamp) -> Stamp.Kind {
        switch stamp {
        case .paid: return .paid
        case .fine: return .fine
        case .contribution: return .contribution
        }
    }
}
