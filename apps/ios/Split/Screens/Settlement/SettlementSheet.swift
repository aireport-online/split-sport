import SplitKit
import SwiftUI

/// "Betaling": record that one member paid another back.
@MainActor
struct SettlementSheet: View {
    @State private var model: SettlementViewModel
    @Environment(\.dismiss) private var dismiss

    init(model: SettlementViewModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        NavigationStack {
            Desk {
                Receipt {
                    MemberMenu(label: "Van", members: model.members, selection: $model.fromMemberId)
                    MemberMenu(label: "Aan", members: model.members, selection: $model.toMemberId)
                    Perforation()
                    AmountField(label: "Bedrag", text: $model.amountText, style: .figure)
                    DatePicker("Datum", selection: dateBinding, displayedComponents: .date)
                    if let message = model.validationMessage {
                        Text(message)
                            .font(.tillSmall)
                            .foregroundStyle(Theme.mutedInk)
                    }
                }
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
            }
            .navigationTitle("Betaling")
            .navigationBarTitleDisplayMode(.inline)
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

    private var dateBinding: Binding<Date> {
        Binding(
            get: { model.date.date() },
            set: { model.date = CalendarDay(date: $0) }
        )
    }
}
