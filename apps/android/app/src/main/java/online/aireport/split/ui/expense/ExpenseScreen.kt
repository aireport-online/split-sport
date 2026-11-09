package online.aireport.split.ui.expense

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.DateField
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MemberDropdown
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
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
        newPhotoUri = viewModel::newPhotoUri,
        onReceiptPhoto = viewModel::scanReceipt,
        onCameraUnavailable = viewModel::onCameraUnavailable,
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
    newPhotoUri: () -> Uri,
    onReceiptPhoto: (Uri) -> Unit,
    onCameraUnavailable: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        topBar = {
            SplitTopBar(
                title = stringResource(if (state.isEditing) R.string.expense_edit else R.string.expense_add),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val group = state.group
        val draft = state.draft
        when {
            group != null && draft != null -> ExpenseForm(
                state = state,
                group = group,
                draft = draft,
                onDraftChange = onDraftChange,
                onSave = onSave,
                onDelete = onDelete,
                newPhotoUri = newPhotoUri,
                onReceiptPhoto = onReceiptPhoto,
                onCameraUnavailable = onCameraUnavailable,
                modifier = Modifier.padding(padding),
            )
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}

@Composable
private fun ExpenseForm(
    state: ExpenseUiState,
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    newPhotoUri: () -> Uri,
    onReceiptPhoto: (Uri) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isBusy
    val preview = state.preview
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SplitCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                AmountField(
                    value = draft.amountText,
                    onValueChange = { onDraftChange(draft.copy(amountText = it)) },
                    label = stringResource(R.string.field_amount),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    // In "Per regel" an empty amount means the total of the lines.
                    placeholder = draft.amountCents?.takeIf { draft.amountText.isBlank() }?.let(::formatAmountInput),
                    imeAction = ImeAction.Done,
                )
                DateField(
                    date = draft.date,
                    onDateChange = { onDraftChange(draft.copy(date = it)) },
                    label = stringResource(R.string.field_date),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                )
                MemberDropdown(
                    label = stringResource(R.string.field_paid_by),
                    members = group.members,
                    selectedId = draft.paidById,
                    onSelect = { onDraftChange(draft.copy(paidById = it)) },
                    myMemberId = group.myMemberId,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                )
            }
        }

        SplitCard {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.split_heading),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                SplitModePicker(
                    mode = draft.mode,
                    onModeChange = { onDraftChange(draft.copy(mode = it)) },
                    enabled = enabled,
                    modifier = Modifier.padding(horizontal = 16.dp),
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
                        newPhotoUri = newPhotoUri,
                        onReceiptPhoto = onReceiptPhoto,
                        onCameraUnavailable = onCameraUnavailable,
                    )
                }
            }
        }

        if (preview != null) SharePreviewCard(preview, group)

        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave,
            loading = state.isSaving,
        )
        if (state.isEditing) {
            SecondaryButton(
                text = stringResource(R.string.action_delete),
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                loading = state.isDeleting,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitModePicker(
    mode: SplitMode,
    onModeChange: (SplitMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Chips rather than a segmented control: four labels do not fit one row at a large font size.
    FlowRow(modifier = modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SplitMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                label = { Text(stringResource(option.labelRes)) },
                enabled = enabled,
            )
        }
    }
}

private val SplitMode.labelRes: Int
    get() = when (this) {
        SplitMode.EQUAL -> R.string.split_mode_equal
        SplitMode.EXACT -> R.string.split_mode_exact
        SplitMode.SHARES -> R.string.split_mode_shares
        SplitMode.ITEMS -> R.string.split_mode_items
    }
