import SplitKit
import SwiftUI

/// "Saldo": every team member, highlighted, with what they have with the other members.
@MainActor
struct BalanceReceipt: View {
    let rows: [GroupDetailViewModel.BalanceRow]

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Saldo")
            ForEach(rows) { row in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    PartyName(party: row.party)
                    if row.isMe {
                        Text("(jij)")
                            .font(.tillSmall)
                            .foregroundStyle(Theme.mutedInk)
                    }
                    if !row.hasAccount {
                        DashedPill(text: "geen account")
                    }
                    Spacer(minLength: 8)
                    Text(row.balance)
                        .foregroundStyle(row.balanceCents == 0 ? Theme.mutedInk : Theme.ink)
                        .fixedSize()
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(accessibilityText(for: row))
            }
        }
    }

    private func accessibilityText(for row: GroupDetailViewModel.BalanceRow) -> String {
        var parts = [row.party.name]
        if row.isMe { parts.append("jij") }
        if !row.hasAccount { parts.append("geen account") }
        parts.append(row.spokenBalance)
        return parts.joined(separator: ", ")
    }
}

/// "Boetekoning": the fine ranking, most first. The first one carries the stamp "KONING".
@MainActor
struct FineRankingReceipt: View {
    let rows: [GroupDetailViewModel.FineRankRow]

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Boetekoning")
            ForEach(rows) { row in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    PartyName(party: row.party)
                    if row.isKing {
                        Stamp(kind: .king)
                    }
                    Spacer(minLength: 8)
                    Text(row.count)
                        .foregroundStyle(Theme.mutedInk)
                    Text(row.total)
                        .frame(minWidth: 64, alignment: .trailing)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(row.isKing ? "\(row.spoken), boetekoning" : row.spoken)
            }
        }
    }
}
