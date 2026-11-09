import SplitKit
import SwiftUI

/// The "Uitgaven" part of a group: one receipt with the expenses per month,
/// newest first. Tapping a line opens it.
@MainActor
struct ExpensesReceipt: View {
    let months: [GroupDetailViewModel.ExpenseMonth]
    let onSelect: (GroupDetailViewModel.ExpenseRow) -> Void

    var body: some View {
        Receipt {
            if months.isEmpty {
                ReceiptTitle(text: "Nog geen uitgaven")
                Text("Voeg de eerste toe.")
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

/// "Boodschappen ........ 64,12", and below it the highlighted payer, the date
/// and what it means for you (or the "BETAALD" stamp on a payment).
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
                    payer
                    Spacer(minLength: 8)
                    outcome
                }
                VStack(alignment: .leading, spacing: 4) {
                    payer
                    outcome
                }
            }
            .font(.tillSmall)
            .foregroundStyle(Theme.mutedInk)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    private var payer: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Highlight(name: row.payerName, color: row.payerColor)
            Text(row.detail)
                .lineLimit(1)
        }
    }

    @ViewBuilder
    private var outcome: some View {
        if row.isSettlement {
            Stamp()
        } else if let impact = row.impact {
            Text(impact)
                .lineLimit(1)
        }
    }
}
