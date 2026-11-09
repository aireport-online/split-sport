import SplitKit
import SwiftUI

/// "Uitgave": add or edit an expense.
@MainActor
struct ExpenseEditorSheet: View {
    @State private var model: ExpenseEditorViewModel
    @State private var isConfirmingDelete = false
    @Environment(\.dismiss) private var dismiss

    init(model: ExpenseEditorViewModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        NavigationStack {
            Form {
                detailsSection
                if model.canScanReceipt {
                    ReceiptScanSection(model: model)
                }
                modeSection
                splitSection
                PerPersonSection(preview: model.preview)
                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }
                if model.isEditing {
                    Section {
                        Button("Verwijderen", role: .destructive) {
                            isConfirmingDelete = true
                        }
                        .disabled(model.isSaving)
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .themedList()
            .navigationTitle(model.isEditing ? "Uitgave" : "Nieuwe uitgave")
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled(model.isSaving || model.isScanning)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuleer") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if model.isSaving {
                        ProgressView()
                    } else {
                        Button("Bewaar") {
                            Task {
                                if await model.save() { dismiss() }
                            }
                        }
                        .disabled(!model.canSave)
                    }
                }
            }
            .confirmationDialog("Uitgave verwijderen?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("Verwijderen", role: .destructive) {
                    Task {
                        if await model.delete() { dismiss() }
                    }
                }
                Button("Annuleer", role: .cancel) {}
            }
        }
    }

    private var detailsSection: some View {
        Section {
            TextField("Omschrijving", text: $model.description)
            LabeledContent("Bedrag") {
                AmountField(label: "Bedrag", text: $model.amountText)
            }
            DatePicker("Datum", selection: dateBinding, displayedComponents: .date)
            Picker("Betaald door", selection: $model.paidById) {
                ForEach(model.members) { member in
                    Text(model.memberName(member.id)).tag(member.id)
                }
            }
        }
        .listRowBackground(Theme.surface)
    }

    private var modeSection: some View {
        Section("Verdeling") {
            Picker("Verdeling", selection: $model.mode) {
                ForEach(SplitMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
        }
        .listRowBackground(Theme.surface)
    }

    @ViewBuilder
    private var splitSection: some View {
        switch model.mode {
        case .equal:
            EqualSplitSection(model: model)
        case .exact:
            ExactSplitSection(model: model)
        case .shares:
            SharesSplitSection(model: model)
        case .items:
            ItemsSplitSection(model: model)
        }
    }

    private var dateBinding: Binding<Date> {
        Binding(
            get: { model.date.date() },
            set: { model.date = CalendarDay(date: $0) }
        )
    }
}

/// The live "Per persoon" summary from the local `computeShares`.
@MainActor
private struct PerPersonSection: View {
    let preview: ExpenseEditorViewModel.Preview

    var body: some View {
        Section("Per persoon") {
            switch preview {
            case .shares(let shares):
                ForEach(shares) { share in
                    LabeledContent(share.name) {
                        Text(share.amount)
                            .monospacedDigit()
                    }
                }
            case .incomplete(let message):
                Text(message)
                    .foregroundStyle(Theme.mutedText)
            }
        }
        .listRowBackground(Theme.surface)
    }
}
