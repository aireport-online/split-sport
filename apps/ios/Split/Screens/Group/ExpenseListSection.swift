import SplitKit
import SwiftUI

/// The "Uitgaven" part of a group: newest first; tapping a row opens it.
@MainActor
struct ExpenseListSection: View {
    let rows: [GroupDetailViewModel.ExpenseRow]
    let onSelect: (GroupDetailViewModel.ExpenseRow) -> Void

    var body: some View {
        if rows.isEmpty {
            Section {
                Text("Nog geen uitgaven. Voeg de eerste toe.")
                    .foregroundStyle(Theme.mutedText)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
                    .padding(.vertical, 24)
            }
            .listRowBackground(Color.clear)
        } else {
            Section {
                ForEach(rows) { row in
                    Button {
                        onSelect(row)
                    } label: {
                        ExpenseRowView(row: row)
                    }
                    .buttonStyle(.plain)
                }
            }
            .listRowBackground(Theme.surface)
        }
    }
}

/// Description, "Anna betaalde · 2 nov", the amount and what it means for you.
@MainActor
private struct ExpenseRowView: View {
    let row: GroupDetailViewModel.ExpenseRow

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: row.isSettlement ? "arrow.left.arrow.right" : "doc.text")
                .foregroundStyle(Theme.mutedText)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Theme.text)
                Text(row.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Theme.mutedText)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(row.amount)
                    .font(.body)
                    .monospacedDigit()
                    .foregroundStyle(Theme.text)
                if let impact = row.impact {
                    Text(impact)
                        .font(.footnote)
                        .foregroundStyle(row.impactSign.color)
                }
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
