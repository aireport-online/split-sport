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
            Form {
                Section {
                    Picker("Van", selection: $model.fromMemberId) {
                        ForEach(model.members) { member in
                            Text(member.name).tag(member.id)
                        }
                    }
                    Picker("Aan", selection: $model.toMemberId) {
                        ForEach(model.members) { member in
                            Text(member.name).tag(member.id)
                        }
                    }
                    LabeledContent("Bedrag") {
                        AmountField(label: "Bedrag", text: $model.amountText)
                    }
                    DatePicker("Datum", selection: dateBinding, displayedComponents: .date)
                } footer: {
                    if let message = model.validationMessage {
                        Text(message)
                    }
                }
                .listRowBackground(Theme.surface)

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .themedList()
            .navigationTitle("Betaling")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleer") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Bewaar") {
                        Task {
                            if await model.save() { dismiss() }
                        }
                    }
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
