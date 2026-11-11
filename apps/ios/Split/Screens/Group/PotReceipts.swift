import SplitKit
import SwiftUI
import UIKit

/// The "Teamkas" receipt: the kasstand as the large figure, what is still to
/// come in and to go out, and the penningmeester with the IBAN to pay to.
@MainActor
struct PotReceipt: View {
    let summary: GroupDetailViewModel.PotSummary

    var body: some View {
        Receipt {
            VStack(alignment: .leading, spacing: 4) {
                Text("KASSTAND")
                Text(summary.cash)
                    .font(.tillFigure)
            }
            .accessibilityElement(children: .combine)
            Perforation()
            LeaderRow(amount: summary.toReceive) {
                Text("Nog te ontvangen")
            }
            .accessibilityElement(children: .combine)
            LeaderRow(amount: summary.toPay) {
                Text("Nog uit te betalen")
            }
            .accessibilityElement(children: .combine)
            Perforation()
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("Penningmeester")
                if let treasurer = summary.treasurer {
                    PartyName(party: treasurer)
                } else {
                    Text("nog niemand")
                        .foregroundStyle(Theme.mutedInk)
                }
            }
            .accessibilityElement(children: .combine)
            if let iban = summary.treasurerIBAN, let ibanText = summary.treasurerIBANText {
                IBANCopyButton(iban: iban, text: ibanText)
            }
        }
    }
}

/// Suggested payments, each with "Markeer als betaald": with the pot under
/// "Met de teamkas", between members under "Vereffenen".
@MainActor
struct TransfersReceipt: View {
    let title: String
    /// What the receipt says when nothing is open.
    let emptyText: String
    let rows: [GroupDetailViewModel.TransferRow]
    let onSettle: (Transfer) -> Void

    var body: some View {
        Receipt {
            ReceiptTitle(text: title)
            if rows.isEmpty {
                Text(emptyText)
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
                PartyName(party: row.payer)
                Image(systemName: "arrow.right")
                    .font(.system(size: 12, weight: .bold))
                PartyName(party: row.receiver)
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
                IBANCopyButton(iban: iban, text: ibanText)
            }
        }
    }
}

/// An IBAN that is copied when tapped, and says so.
@MainActor
struct IBANCopyButton: View {
    /// The IBAN as stored, for the clipboard.
    let iban: String
    /// The IBAN in groups of four, for reading.
    let text: String
    @State private var isCopied = false

    var body: some View {
        Button {
            UIPasteboard.general.string = iban
            isCopied = true
        } label: {
            Label(isCopied ? "Gekopieerd" : text, systemImage: isCopied ? "checkmark" : "doc.on.doc")
                .font(.tillSmall)
                .foregroundStyle(Theme.mutedInk)
                .frame(minHeight: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Kopieer IBAN \(text)")
    }
}
