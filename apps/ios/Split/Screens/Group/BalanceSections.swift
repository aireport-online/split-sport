import SplitKit
import SwiftUI
import UIKit

/// The "Saldo" part of a group: every member's balance and, under "Vereffenen",
/// the payments that settle the group.
@MainActor
struct BalanceSections: View {
    let balances: [GroupDetailViewModel.BalanceRow]
    let transfers: [GroupDetailViewModel.TransferRow]
    let onSettle: (Transfer) -> Void
    /// The suggested payment whose IBAN was copied last, to confirm it visibly.
    @State private var copiedTransferID: String?

    var body: some View {
        Section {
            ForEach(balances) { row in
                HStack(spacing: 12) {
                    InitialsBadge(initials: row.initials, color: row.tint, outline: .circle)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.name)
                            .foregroundStyle(Theme.text)
                        if !row.hasAccount {
                            Text("geen account")
                                .font(.footnote)
                                .foregroundStyle(Theme.mutedText)
                        }
                    }
                    Spacer(minLength: 8)
                    Text(row.balance)
                        .font(.callout)
                        .foregroundStyle(row.sign.color)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .listRowBackground(Theme.surface)

        Section("Vereffenen") {
            if transfers.isEmpty {
                Text("Iedereen staat quitte.")
                    .foregroundStyle(Theme.mutedText)
            } else {
                ForEach(transfers) { row in
                    transferRow(row)
                }
            }
        }
        .listRowBackground(Theme.surface)
    }

    private func transferRow(_ row: GroupDetailViewModel.TransferRow) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(row.text)
                .foregroundStyle(Theme.text)
            if let iban = row.iban, let ibanText = row.ibanText {
                Button {
                    UIPasteboard.general.string = iban
                    copiedTransferID = row.id
                } label: {
                    Label(ibanText, systemImage: copiedTransferID == row.id ? "checkmark" : "doc.on.doc")
                        .font(.footnote.monospaced())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Kopieer IBAN van \(row.receiverName), \(ibanText)")
                .accessibilityValue(copiedTransferID == row.id ? "Gekopieerd" : "")
            }
            Button("Markeer als betaald") {
                onSettle(row.transfer)
            }
            .buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }
}
