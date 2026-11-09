import SplitKit
import SwiftUI
import UIKit

/// "Saldo": every member, highlighted, with their signed balance.
@MainActor
struct BalanceReceipt: View {
    let rows: [GroupDetailViewModel.BalanceRow]

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Saldo")
            ForEach(rows) { row in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Highlight(name: row.name, color: row.color)
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
        var parts = [row.name]
        if row.isMe { parts.append("jij") }
        if !row.hasAccount { parts.append("geen account") }
        parts.append(row.spokenBalance)
        return parts.joined(separator: ", ")
    }
}

/// "Vereffenen": the payments that settle the group, each with "Markeer als betaald".
@MainActor
struct SettleReceipt: View {
    let rows: [GroupDetailViewModel.TransferRow]
    let onSettle: (Transfer) -> Void
    /// The suggested payment whose IBAN was copied last, to confirm it visibly.
    @State private var copiedTransferID: String?

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Vereffenen")
            if rows.isEmpty {
                Text("Iedereen staat quitte.")
                    .foregroundStyle(Theme.mutedInk)
            } else {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    if index > 0 {
                        Perforation()
                    }
                    transfer(row)
                }
            }
        }
    }

    private func transfer(_ row: GroupDetailViewModel.TransferRow) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Highlight(name: row.payerName, color: row.payerColor)
                Image(systemName: "arrow.right")
                    .font(.system(size: 12, weight: .bold))
                Highlight(name: row.receiverName, color: row.receiverColor)
                Spacer(minLength: 8)
                Text(row.amount)
                    .fixedSize()
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(row.spoken)

            Button("Markeer als betaald") {
                onSettle(row.transfer)
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))

            if let iban = row.iban, let ibanText = row.ibanText {
                Button {
                    UIPasteboard.general.string = iban
                    copiedTransferID = row.id
                } label: {
                    Label(
                        copiedTransferID == row.id ? "Gekopieerd" : ibanText,
                        systemImage: copiedTransferID == row.id ? "checkmark" : "doc.on.doc"
                    )
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
                    .frame(minHeight: 28)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Kopieer IBAN van \(row.receiverName), \(ibanText)")
            }
        }
    }
}
