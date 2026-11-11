import SplitKit
import SwiftUI

/// "Uitgave": add or edit an expense.
@MainActor
struct ExpenseEditorSheet: View {
    @State private var model: ExpenseEditorViewModel
    @State private var isConfirmingDelete = false
    /// Whether the "Rit berekenen" helper is unfolded.
    @State private var showsTrip = false
    @Environment(\.dismiss) private var dismiss

    init(model: ExpenseEditorViewModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        NavigationStack {
            Desk {
                detailsReceipt
                if showsTrip {
                    TripReceipt(model: model)
                }
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

    /// Omschrijving, bedrag, betaald door (a team member or the Teamkas) and datum.
    private var detailsReceipt: some View {
        Receipt {
            PaperField(label: "Omschrijving", text: $model.description)
            AmountField(label: "Bedrag", text: $model.amountText, style: .figure)
            if !showsTrip {
                LinkButton(title: "Rit berekenen") {
                    showsTrip = true
                }
                .font(.tillSmall)
            }
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
                        PartyName(party: share.party)
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

/// "Rit berekenen": kilometres × a rate per kilometre fills in the amount and,
/// when it is still empty, the description.
@MainActor
private struct TripReceipt: View {
    @Bindable var model: ExpenseEditorViewModel

    var body: some View {
        Receipt {
            ReceiptTitle(text: "Rit berekenen")
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("Kilometers")
                    .accessibilityHidden(true)
                Spacer(minLength: 8)
                AmountField(label: "Kilometers", text: $model.tripKilometersText, prompt: "0")
            }
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("Tarief per km")
                    .accessibilityHidden(true)
                Spacer(minLength: 8)
                AmountField(label: "Tarief per kilometer in euro", text: $model.tripRateText)
            }
            if let trip = model.trip {
                Text(trip.summary)
                    .font(.tillSmall)
                    .foregroundStyle(Theme.mutedInk)
            }
            Button("Invullen") {
                model.applyTrip()
            }
            .buttonStyle(PaperButtonStyle(isCompact: true))
            .disabled(model.trip == nil)
        }
    }
}
