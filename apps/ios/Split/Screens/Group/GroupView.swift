import SplitKit
import SwiftUI

/// "Team": everything that happened, the team pot and the balances of one team.
@MainActor
struct GroupView: View {
    private enum Part: Hashable {
        case entries, pot, balances
    }

    private enum Sheet: Identifiable {
        case newExpense
        case editExpense(Expense)
        case fine
        case contribution
        case settlement(Transfer)
        case settings

        var id: String {
            switch self {
            case .newExpense: return "newExpense"
            case .editExpense(let expense): return "expense-\(expense.id)"
            case .fine: return "fine"
            case .contribution: return "contribution"
            case .settlement(let transfer): return "settlement-\(transfer.fromMemberId)-\(transfer.toMemberId)"
            case .settings: return "settings"
            }
        }
    }

    let session: AppSession
    let onLeave: () -> Void
    @State private var model: GroupDetailViewModel
    @State private var part = Part.entries
    @State private var sheet: Sheet?
    /// The payment, fine or contribution the person tapped, waiting for "Verwijderen".
    @State private var entryToRemove: GroupDetailViewModel.ExpenseRow?

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
                // The team's name, underlined with its highlighter.
                ToolbarItem(placement: .principal) {
                    GroupNameText(name: model.name, color: model.color)
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                        .accessibilityAddTraits(.isHeader)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        sheet = .settings
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Teaminstellingen")
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
                entryToRemove?.title ?? "",
                isPresented: isConfirmingRemoval,
                titleVisibility: .visible
            ) {
                Button("Verwijderen", role: .destructive) {
                    if let row = entryToRemove {
                        Task { await model.delete(row.expense) }
                    }
                    entryToRemove = nil
                }
                Button("Annuleren", role: .cancel) {
                    entryToRemove = nil
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if model.detail != nil {
            Desk {
                SegmentedControl(
                    label: "Onderdeel",
                    options: [
                        (value: Part.entries, title: "Uitgaven"),
                        (value: Part.pot, title: "Teamkas"),
                        (value: Part.balances, title: "Saldo"),
                    ],
                    selection: $part
                )
                if let message = model.errorMessage {
                    ErrorBanner(message: message)
                }
                switch part {
                case .entries:
                    entriesPart
                case .pot:
                    potPart
                case .balances:
                    balancesPart
                }
            }
            .refreshable { await model.load() }
            .safeAreaInset(edge: .bottom) {
                actions
            }
        } else if let message = model.errorMessage {
            LoadFailedView(message: message) { await model.load() }
        } else {
            LoadingView()
        }
    }

    private var entriesPart: some View {
        ExpensesReceipt(months: model.expenseMonths) { row in
            // Only an ordinary expense can be changed; the rest can only be removed.
            if row.isEditable {
                sheet = .editExpense(row.expense)
            } else {
                entryToRemove = row
            }
        }
    }

    @ViewBuilder
    private var potPart: some View {
        if let summary = model.potSummary {
            PotReceipt(summary: summary)
        }
        TransfersReceipt(
            title: "Met de teamkas",
            emptyText: "Niemand heeft nog iets openstaan bij de teamkas.",
            rows: model.potTransferRows
        ) { transfer in
            sheet = .settlement(transfer)
        }
    }

    @ViewBuilder
    private var balancesPart: some View {
        BalanceReceipt(rows: model.balanceRows)
        TransfersReceipt(title: "Vereffenen", emptyText: "Iedereen staat quitte.", rows: model.peerTransferRows) { transfer in
            sheet = .settlement(transfer)
        }
        // Hidden while nobody has a fine.
        if !model.fineRanking.isEmpty {
            FineRankingReceipt(rows: model.fineRanking)
        }
    }

    /// "Uitgave toevoegen" as the primary action, "Boete geven" and "Inleg vragen" right below it.
    private var actions: some View {
        BottomBar {
            VStack(spacing: 8) {
                Button("Uitgave toevoegen") {
                    sheet = .newExpense
                }
                .buttonStyle(PaperButtonStyle(kind: .primary))
                HStack(spacing: 8) {
                    Button("Boete geven") {
                        sheet = .fine
                    }
                    Button("Inleg vragen") {
                        sheet = .contribution
                    }
                }
                .buttonStyle(PaperButtonStyle(isCompact: true, fillsWidth: true))
            }
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
            case .fine:
                ChargeSheet(model: ChargeViewModel(api: session.api, group: group, type: .fine))
            case .contribution:
                ChargeSheet(model: ChargeViewModel(api: session.api, group: group, type: .contribution))
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

    private var isConfirmingRemoval: Binding<Bool> {
        Binding(
            get: { entryToRemove != nil },
            set: { isShown in
                if !isShown { entryToRemove = nil }
            }
        )
    }
}
