package online.aireport.split.ui.expense

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import online.aireport.split.R
import online.aireport.split.core.SplitMode
import online.aireport.split.core.formatAmountInput
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.BottomActionBar
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.DateField
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.LinkButton
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MemberPicker
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.Segmented
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar

private const val MAX_DESCRIPTION_LENGTH = 120

@Composable
fun ExpenseRoute(groupId: String, expenseId: String?, onDone: () -> Unit) {
    val viewModel = splitViewModel {
        ExpenseViewModel(
            groupId = groupId,
            expenseId = expenseId,
            savedState = createSavedStateHandle(),
            groupRepository = it.groupRepository,
            expenseRepository = it.expenseRepository,
            receiptRepository = it.receiptRepository,
            receiptImageReader = it.receiptImageReader,
            receiptPhotoFiles = it.receiptPhotoFiles,
        )
    }
    val state by viewModel.uiState.collectAsUiState()
    val receiptActions = remember(viewModel) {
        ReceiptActions(
            newPhotoUri = viewModel::newPhotoUri,
            onPhoto = viewModel::scanReceipt,
            onCameraUnavailable = viewModel::onCameraUnavailable,
        )
    }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    ExpenseScreen(
        state = state,
        onBack = onDone,
        onRetry = viewModel::retry,
        onDraftChange = viewModel::onDraftChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        receiptActions = receiptActions,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun ExpenseScreen(
    state: ExpenseUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDraftChange: (ExpenseDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    receiptActions: ReceiptActions,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val group = state.group
    val draft = state.draft
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            SplitTopBar(
                title = stringResource(if (state.isEditing) R.string.expense_edit else R.string.expense_add),
                onBack = onBack,
            )
        },
        bottomBar = {
            if (draft != null) {
                BottomActionBar {
                    PrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canSave,
                        loading = state.isSaving,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            group != null && draft != null -> ExpenseForm(
                state = state,
                group = group,
                draft = draft,
                onDraftChange = onDraftChange,
                onDelete = onDelete,
                receiptActions = receiptActions,
                modifier = Modifier.padding(padding),
            )
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}

/** The form as one receipt: the expense, a perforation, the split, a perforation, "Per persoon". */
@Composable
private fun ExpenseForm(
    state: ExpenseUiState,
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    onDelete: () -> Unit,
    receiptActions: ReceiptActions,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isBusy
    val preview = state.preview
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Receipt(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SplitTextField(
                    value = draft.description,
                    onValueChange = { onDraftChange(draft.copy(description = it.take(MAX_DESCRIPTION_LENGTH))) },
                    label = stringResource(R.string.field_description),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                Column {
                    AmountField(
                        value = draft.amountText,
                        onValueChange = { onDraftChange(draft.copy(amountText = it)) },
                        label = stringResource(R.string.field_amount),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        figure = true,
                        // In "Per regel" an empty amount means the total of the lines.
                        placeholder = draft.amountCents?.takeIf { draft.amountText.isBlank() }?.let(::formatAmountInput),
                        imeAction = ImeAction.Done,
                    )
                    TripButton(
                        enabled = enabled,
                        onTrip = { costCents, description -> onDraftChange(draft.withTrip(costCents, description)) },
                        // Lines the text of the link up with the fields; its touch area sticks out.
                        modifier = Modifier.offset(x = (-8).dp),
                    )
                }
                DateField(
                    date = draft.date,
                    onDateChange = { onDraftChange(draft.copy(date = it)) },
                    label = stringResource(R.string.field_date),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                )
                MemberPicker(
                    label = stringResource(R.string.field_paid_by),
                    members = group.members,
                    selectedId = draft.paidById,
                    onSelect = { onDraftChange(draft.copy(paidById = it)) },
                    myMemberId = group.myMemberId,
                    enabled = enabled,
                )
            }

            Segmented(
                options = SplitMode.entries,
                selected = draft.mode,
                onSelect = { onDraftChange(draft.copy(mode = it)) },
                label = { stringResource(it.labelRes) },
                modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 4.dp, bottom = 8.dp),
                enabled = enabled,
            )
            when (draft.mode) {
                SplitMode.EQUAL -> EqualSplitEditor(group, draft, preview, onDraftChange, enabled)
                SplitMode.EXACT -> ExactSplitEditor(group, draft, onDraftChange, enabled)
                SplitMode.SHARES -> SharesSplitEditor(group, draft, onDraftChange, enabled)
                SplitMode.ITEMS -> ItemsSplitEditor(
                    group = group,
                    draft = draft,
                    onDraftChange = onDraftChange,
                    enabled = enabled,
                    scanEnabled = state.scanEnabled,
                    isScanning = state.isScanning,
                    receiptActions = receiptActions,
                )
            }

            if (preview != null) {
                Perforation(Modifier.padding(horizontal = ReceiptPadding, vertical = 10.dp))
                SharePreview(preview, group)
            }
        }

        if (state.isEditing) {
            LinkButton(
                text = stringResource(R.string.action_delete),
                onClick = { confirmDelete = true },
                modifier = Modifier.padding(top = 12.dp),
                enabled = enabled,
                destructive = true,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.expense_delete_title),
            text = stringResource(R.string.expense_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val SplitMode.labelRes: Int
    get() = when (this) {
        SplitMode.EQUAL -> R.string.split_mode_equal
        SplitMode.EXACT -> R.string.split_mode_exact
        SplitMode.SHARES -> R.string.split_mode_shares
        SplitMode.ITEMS -> R.string.split_mode_items
    }

/** What the "Bon scannen" button needs from the view model. */
class ReceiptActions(
    /** A `content://` URI the camera app may write a photo to. */
    val newPhotoUri: () -> Uri,
    /** A photo of a receipt was taken or picked. */
    val onPhoto: (Uri) -> Unit,
    val onCameraUnavailable: () -> Unit,
)
