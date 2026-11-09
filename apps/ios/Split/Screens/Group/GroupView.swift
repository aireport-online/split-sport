import SplitKit
import SwiftUI

/// "Groep": the expenses and the balances of one group.
@MainActor
struct GroupView: View {
    private enum Part: String, CaseIterable, Identifiable {
        case expenses = "Uitgaven"
        case balances = "Saldo"

        var id: String { rawValue }
    }

    private enum Sheet: Identifiable {
        case newExpense
        case editExpense(Expense)
        case settlement(Transfer)
        case settings

        var id: String {
            switch self {
            case .newExpense: return "newExpense"
            case .editExpense(let expense): return "expense-\(expense.id)"
            case .settlement(let transfer): return "settlement-\(transfer.fromMemberId)-\(transfer.toMemberId)"
            case .settings: return "settings"
            }
        }
    }

    let session: AppSession
    let onLeave: () -> Void
    @State private var model: GroupDetailViewModel
    @State private var part = Part.expenses
    @State private var sheet: Sheet?
    /// The payment the person tapped, waiting for "Betaling verwijderen".
    @State private var settlementToDelete: GroupDetailViewModel.ExpenseRow?

    init(session: AppSession, route: GroupRoute, onLeave: @escaping () -> Void) {
        self.session = session
        self.onLeave = onLeave
        _model = State(initialValue: GroupDetailViewModel(
            api: session.api, groupId: route.id, name: route.name, color: route.color
        ))
    }

    var body: some View {
        content
            .navigationTitle(model.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        sheet = .settings
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Groepsinstellingen")
                    .disabled(model.detail == nil)
                }
                ToolbarItem(placement: .bottomBar) {
                    Button {
                        sheet = .newExpense
                    } label: {
                        Label("Uitgave toevoegen", systemImage: "plus.circle.fill")
                            .labelStyle(.titleAndIcon)
                            .font(.headline)
                    }
                    .disabled(model.detail == nil)
                }
            }
            .task { await model.load() }
            .sheet(item: $sheet, onDismiss: { Task { await model.load() } }) { sheet in
                sheetContent(sheet)
            }
            .alert("Dat is niet gelukt", isPresented: isShowingActionError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(model.actionError ?? "")
            }
            .confirmationDialog(
                settlementToDelete?.title ?? "",
                isPresented: isConfirmingSettlementDelete,
                titleVisibility: .visible
            ) {
                Button("Betaling verwijderen", role: .destructive) {
                    if let row = settlementToDelete {
                        Task { await model.delete(row.expense) }
                    }
                    settlementToDelete = nil
                }
                Button("Annuleer", role: .cancel) {
                    settlementToDelete = nil
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if model.detail != nil {
            List {
                Section {
                    Picker("Onderdeel", selection: $part) {
                        ForEach(Part.allCases) { part in
                            Text(part.rawValue).tag(part)
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                if let message = model.errorMessage {
                    Section {
                        ErrorRow(message: message)
                    }
                    .listRowBackground(Theme.surface)
                }

                switch part {
                case .expenses:
                    ExpenseListSection(rows: model.expenseRows) { row in
                        // A payment cannot be edited, only removed.
                        if row.isSettlement {
                            settlementToDelete = row
                        } else {
                            sheet = .editExpense(row.expense)
                        }
                    }
                case .balances:
                    BalanceSections(
                        balances: model.balanceRows,
                        transfers: model.transferRows,
                        onSettle: { transfer in sheet = .settlement(transfer) }
                    )
                }
            }
            .themedList()
            .refreshable { await model.load() }
        } else if let message = model.errorMessage {
            LoadFailedView(message: message) { await model.load() }
        } else {
            LoadingView()
        }
    }

    @ViewBuilder
    private func sheetContent(_ sheet: Sheet) -> some View {
        if let group = model.detail {
            switch sheet {
            case .newExpense:
                ExpenseEditorSheet(model: ExpenseEditorViewModel(
                    api: session.api, group: group, canScanReceipt: session.receiptScanEnabled
                ))
            case .editExpense(let expense):
                ExpenseEditorSheet(model: ExpenseEditorViewModel(
                    api: session.api, group: group, expense: expense, canScanReceipt: session.receiptScanEnabled
                ))
            case .settlement(let transfer):
                SettlementSheet(model: SettlementViewModel(api: session.api, group: group, transfer: transfer))
            case .settings:
                GroupSettingsSheet(
                    model: GroupSettingsViewModel(
                        api: session.api, group: group, serverURL: session.serverURL,
                        onChange: { updated in model.apply(updated) }
                    ),
                    onLeave: onLeave
                )
            }
        }
    }

    private var isShowingActionError: Binding<Bool> {
        Binding(
            get: { model.actionError != nil },
            set: { isShown in
                if !isShown { model.actionError = nil }
            }
        )
    }

    private var isConfirmingSettlementDelete: Binding<Bool> {
        Binding(
            get: { settlementToDelete != nil },
            set: { isShown in
                if !isShown { settlementToDelete = nil }
            }
        )
    }
}
