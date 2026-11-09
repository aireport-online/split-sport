package online.aireport.split.ui.group

import android.content.ClipData
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import online.aireport.split.R
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.BottomActionBar
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Segmented
import online.aireport.split.ui.components.SplitTopBar

@Composable
fun GroupRoute(
    groupId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
) {
    val viewModel = splitViewModel {
        GroupViewModel(groupId, it.groupRepository, it.expenseRepository, it.groupChanges)
    }
    val state by viewModel.uiState.collectAsUiState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val ibanLabel = stringResource(R.string.iban_label)
    val copiedMessage = stringResource(R.string.iban_copied)

    GroupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onAddExpense = onAddExpense,
        onEditExpense = onEditExpense,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onMarkAsPaid = viewModel::onMarkAsPaid,
        onCopyIban = { iban ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(ibanLabel, iban)))
                // Android 13 and later show their own confirmation of what was copied.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbarHostState.showSnackbar(copiedMessage)
                }
            }
        },
        onSettlementChange = viewModel::onSettlementChange,
        onSettlementSave = viewModel::saveSettlement,
        onSettlementDismiss = viewModel::onSettlementDismiss,
        onDeleteSettlement = viewModel::deleteSettlement,
        onMessageShown = viewModel::onMessageShown,
    )
}

enum class GroupTab(@param:StringRes val labelRes: Int) {
    EXPENSES(R.string.group_tab_expenses),
    BALANCES(R.string.group_tab_balances),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: GroupUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMarkAsPaid: (Transfer) -> Unit,
    onCopyIban: (String) -> Unit,
    onSettlementChange: (SettlementForm) -> Unit,
    onSettlementSave: () -> Unit,
    onSettlementDismiss: () -> Unit,
    onDeleteSettlement: (String) -> Unit,
    onMessageShown: () -> Unit,
    initialTab: GroupTab = GroupTab.EXPENSES,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var openSettlementId by rememberSaveable { mutableStateOf<String?>(null) }
    val group = state.group
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        topBar = {
            SplitTopBar(
                title = group?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (group != null) {
                        IconActionButton(
                            icon = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                            onClick = onOpenSettings,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (group != null) {
                BottomActionBar {
                    PrimaryButton(stringResource(R.string.expense_add), onAddExpense, Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            group != null -> Column(Modifier.padding(padding)) {
                Segmented(
                    options = GroupTab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    label = { stringResource(it.labelRes) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (tab) {
                        GroupTab.EXPENSES -> ExpensesTab(
                            group = group,
                            expenses = state.expenses,
                            onOpenExpense = { expense ->
                                when (expense.kind) {
                                    ExpenseKind.EXPENSE -> onEditExpense(expense.id)
                                    ExpenseKind.SETTLEMENT -> openSettlementId = expense.id
                                }
                            },
                        )
                        GroupTab.BALANCES -> BalancesTab(group, onMarkAsPaid, onCopyIban)
                    }
                }
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }

    if (group != null && state.settlement != null) {
        SettlementSheet(
            form = state.settlement,
            group = group,
            onChange = onSettlementChange,
            onSave = onSettlementSave,
            onDismiss = onSettlementDismiss,
        )
    }

    val openSettlement: Expense? = state.expenses.firstOrNull { it.id == openSettlementId }
    if (group != null && openSettlement != null) {
        SettlementDialog(
            settlement = openSettlement,
            group = group,
            onDelete = {
                openSettlementId = null
                onDeleteSettlement(openSettlement.id)
            },
            onDismiss = { openSettlementId = null },
        )
    }
}
