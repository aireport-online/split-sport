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
            Desk {
                detailsReceipt
                if model.canScanReceipt {
                    ReceiptScanReceipt(model: model)
                }
                splitReceipt
                PerPersonReceipt(preview: model.preview, total: model.totalText)
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                if model.isEditing {
                    Button("Verwijderen") {
                        isConfirmingDelete = true
                    }
                    .buttonStyle(PaperButtonStyle(kind: .destructive))
                    .disabled(model.isSaving)
                }
            }
            .navigationTitle(model.isEditing ? "Uitgave" : "Uitgave toevoegen")
            .navigationBarTitleDisplayMode(.inline)
            .interactiveDismissDisabled(model.isSaving || model.isScanning)
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
            .confirmationDialog("Uitgave verwijderen?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("Verwijderen", role: .destructive) {
                    Task {
                        if await model.delete() { dismiss() }
                    }
                }
                Button("Annuleren", role: .cancel) {}
            }
        }
    }

    /// Omschrijving, bedrag, betaald door and datum.
    private var detailsReceipt: some View {
        Receipt {
            PaperField(label: "Omschrijving", text: $model.description)
            AmountField(label: "Bedrag", text: $model.amountText, style: .figure)
            MemberMenu(label: "Betaald door", members: model.members, selection: $model.paidById)
            DatePicker("Datum", selection: dateBinding, displayedComponents: .date)
        }
    }

    /// "Verdeling": the four ways to divide the expense.
    private var splitReceipt: some View {
        Receipt {
            SegmentedControl(
                label: "Verdeling",
                options: SplitMode.allCases.map { (value: $0, title: $0.title) },
                selection: $model.mode
            )
            switch model.mode {
            case .equal:
                EqualSplitRows(model: model)
            case .exact:
                ExactSplitRows(model: model)
            case .shares:
                SharesSplitRows(model: model)
            case .items:
                ItemsSplitRows(model: model)
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

/// The live "Per persoon" summary from the local `computeShares`, with the total below it.
@MainActor
private struct PerPersonReceipt: View {
    let preview: ExpenseEditorViewModel.Preview
    let total: String?

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Per persoon")
            switch preview {
            case .shares(let shares):
                ForEach(shares) { share in
                    LeaderRow(amount: share.amount) {
                        Highlight(name: share.name, color: share.color)
                    }
                    .accessibilityElement(children: .combine)
                }
                if let total {
                    Perforation()
                    HStack(alignment: .firstTextBaseline) {
                        Text("TOTAAL")
                        Spacer(minLength: 8)
                        Text(total)
                            .font(.tillTotal)
                    }
                    .accessibilityElement(children: .combine)
                }
            case .incomplete(let message):
                Text(message)
                    .foregroundStyle(Theme.mutedInk)
            }
        }
    }
}
