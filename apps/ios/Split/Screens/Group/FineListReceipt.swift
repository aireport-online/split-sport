import SplitKit
import SwiftUI

/// "Boetelijst": the lines "Boete geven" offers, with a way to add, change and remove them.
@MainActor
struct FineListReceipt: View {
    @Bindable var model: GroupSettingsViewModel
    /// The line being changed, with what is typed for it.
    @State private var fineToChange: Fine?
    @State private var changedName = ""
    @State private var changedAmountText = ""

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Boetelijst")
            if model.detail.fines.isEmpty {
                Text("Nog geen boetes op de lijst. Voeg de eerste toe.")
                    .foregroundStyle(Theme.mutedInk)
            }
            ForEach(model.detail.fines) { fine in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    LeaderRow(amount: Money.plain(fine.amountCents)) {
                        Text(fine.name)
                            .lineLimit(2)
                    }
                    .accessibilityElement(children: .combine)
                    fineMenu(fine)
                }
            }
            Perforation()
            PaperField(label: "Nieuwe boete", text: $model.newFineName)
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("Bedrag")
                    .accessibilityHidden(true)
                Spacer(minLength: 8)
                AmountField(label: "Bedrag van de nieuwe boete", text: $model.newFineAmountText)
            }
            Button("Voeg toe") {
                Task { await model.addFine() }
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(!model.canAddFine)
        }
        .alert("Boete wijzigen", isPresented: isChanging) {
            TextField("Naam", text: $changedName)
            TextField("Bedrag", text: $changedAmountText)
            Button("Opslaan") {
                if let fine = fineToChange {
                    Task { await model.updateFine(id: fine.id, name: changedName, amountText: changedAmountText) }
                }
                fineToChange = nil
            }
            Button("Annuleren", role: .cancel) {
                fineToChange = nil
            }
        }
    }

    private func fineMenu(_ fine: Fine) -> some View {
        Menu {
            Button {
                changedName = fine.name
                changedAmountText = Money.editingText(fine.amountCents)
                fineToChange = fine
            } label: {
                Label("Wijzig", systemImage: "pencil")
            }
            Button(role: .destructive) {
                Task { await model.removeFine(id: fine.id) }
            } label: {
                Label("Verwijder", systemImage: "trash")
            }
        } label: {
            Image(systemName: "ellipsis")
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Acties voor \(fine.name)")
    }

    private var isChanging: Binding<Bool> {
        Binding(
            get: { fineToChange != nil },
            set: { isShown in
                if !isShown { fineToChange = nil }
            }
        )
    }
}
