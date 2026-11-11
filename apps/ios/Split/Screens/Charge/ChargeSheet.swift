import SplitKit
import SwiftUI

/// "Boete geven" and "Inleg vragen": who, what for, how much per person and when.
@MainActor
struct ChargeSheet: View {
    @State private var model: ChargeViewModel
    @Environment(\.dismiss) private var dismiss

    private let chipColumns = [GridItem(.adaptive(minimum: 96), spacing: 6, alignment: .leading)]

    init(model: ChargeViewModel) {
        _model = State(initialValue: model)
    }

    private var isFine: Bool {
        model.type == .fine
    }

    var body: some View {
        NavigationStack {
            Desk {
                membersReceipt
                if isFine {
                    fineListReceipt
                }
                detailsReceipt
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
            }
            .navigationTitle(isFine ? "Boete geven" : "Inleg vragen")
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled(model.isSaving)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleren") { dismiss() }
                        .font(.buttonLabel)
                }
            }
            .safeAreaInset(edge: .bottom) {
                BottomBar {
                    Button {
                        Task {
                            if await model.save() { dismiss() }
                        }
                    } label: {
                        BusyLabel(title: "Opslaan", isBusy: model.isSaving)
                    }
                    .buttonStyle(PaperButtonStyle(kind: .primary))
                    .disabled(!model.canSave)
                }
            }
        }
    }

    /// Who: the team members as chips.
    private var membersReceipt: some View {
        Receipt {
            ReceiptTitle(text: isFine ? "Wie krijgt de boete?" : "Wie legt in?")
            LazyVGrid(columns: chipColumns, alignment: .leading, spacing: 6) {
                ForEach(model.members) { member in
                    PartyChip(party: member, isSelected: model.selectedMemberIds.contains(member.id)) {
                        model.toggleMember(member.id)
                    }
                }
            }
        }
    }

    /// The lines of the fine list as buttons: "Te laat 2,00".
    private var fineListReceipt: some View {
        Receipt {
            ReceiptTitle(text: "Boetelijst")
            if model.fines.isEmpty {
                Text("De boetelijst is leeg. Typ hieronder zelf een omschrijving en een bedrag.")
                    .foregroundStyle(Theme.mutedInk)
            }
            ForEach(model.fines) { fine in
                FineButton(fine: fine, isChosen: model.isChosen(fine)) {
                    model.choose(fine)
                }
            }
        }
    }

    /// Omschrijving, bedrag, datum and the total.
    private var detailsReceipt: some View {
        Receipt {
            PaperField(label: "Omschrijving", text: $model.description)
            AmountField(label: isFine ? "Bedrag" : "Bedrag per persoon", text: $model.amountText, style: .figure)
            DatePicker("Datum", selection: dateBinding, displayedComponents: .date)
            Perforation()
            if let total = model.totalText {
                Text(total)
                    .font(.tillBold)
            } else if let message = model.validationMessage {
                Text(message)
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
            }
        }
    }

    private var dateBinding: Binding<Date> {
        Binding(
            get: { model.date.date() },
            set: { model.date = CalendarDay(date: $0) }
        )
    }
}

/// One line of the fine list to pick: filled with ink when it is the one chosen.
@MainActor
private struct FineButton: View {
    let fine: Fine
    let isChosen: Bool
    let choose: () -> Void

    var body: some View {
        Button(action: choose) {
            Text("\(fine.name)  \(Money.plain(fine.amountCents))")
        }
        .buttonStyle(PaperButtonStyle(kind: isChosen ? .primary : .secondary, isCompact: true))
        .accessibilityLabel("\(fine.name), \(Money.euro(fine.amountCents))")
        .accessibilityAddTraits(isChosen ? .isSelected : [])
    }
}
