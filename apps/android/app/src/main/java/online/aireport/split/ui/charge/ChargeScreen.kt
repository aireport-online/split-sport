package online.aireport.split.ui.charge

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.BottomActionBar
import online.aireport.split.ui.components.DateField
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.PersonChip
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar
import online.aireport.split.ui.theme.SplitTheme

private const val MAX_DESCRIPTION_LENGTH = 120

@Composable
fun ChargeRoute(groupId: String, type: ChargeType, onDone: () -> Unit) {
    val viewModel = splitViewModel { ChargeViewModel(groupId, type, it.groupRepository, it.expenseRepository) }
    val state by viewModel.uiState.collectAsUiState()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
    }

    ChargeScreen(
        state = state,
        onBack = onDone,
        onRetry = viewModel::retry,
        onFormChange = viewModel::onFormChange,
        onSave = viewModel::save,
        onMessageShown = viewModel::onMessageShown,
    )
}

/** "Boete geven" or "Inleg vragen", depending on the type of the charge. */
@Composable
fun ChargeScreen(
    state: ChargeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFormChange: (ChargeForm) -> Unit,
    onSave: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val group = state.group
    val form = state.form
    val isFine = state.type == ChargeType.FINE
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            SplitTopBar(
                title = stringResource(if (isFine) R.string.fine_give else R.string.contribution_ask),
                onBack = onBack,
            )
        },
        bottomBar = {
            if (form != null) {
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
            group != null && form != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            ) {
                Receipt(Modifier.fillMaxWidth()) {
                    val enabled = !state.isSaving
                    if (isFine) {
                        FineForm(form, group, onFormChange, enabled)
                    } else {
                        ContributionForm(form, group, onFormChange, enabled)
                    }
                    ChargeTotal(form)
                }
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}

/** Who, a line of the fine list or an own description and amount, and the date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FineForm(form: ChargeForm, group: GroupDetail, onFormChange: (ChargeForm) -> Unit, enabled: Boolean) {
    Section(stringResource(R.string.charge_who)) {
        MemberChips(form, group, onFormChange, enabled)
    }
    Perforation(Modifier.padding(horizontal = ReceiptPadding))
    if (group.fines.isNotEmpty()) {
        Section(stringResource(R.string.fine_list)) {
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.fines.forEach { fine ->
                    PersonChip(
                        name = stringResource(R.string.fine_list_option, fine.name, formatAmount(fine.amountCents)),
                        selected = form.isFine(fine),
                        onSelectedChange = { onFormChange(form.withFine(fine)) },
                        enabled = enabled,
                        role = Role.RadioButton,
                    )
                }
            }
        }
        Perforation(Modifier.padding(horizontal = ReceiptPadding))
    }
    ChargeFields(
        form = form,
        onFormChange = onFormChange,
        enabled = enabled,
        amountLabel = stringResource(R.string.field_amount),
        descriptionPlaceholder = null,
    )
}

/** The description, the amount per person, the date, and who (everyone to begin with). */
@Composable
private fun ContributionForm(form: ChargeForm, group: GroupDetail, onFormChange: (ChargeForm) -> Unit, enabled: Boolean) {
    ChargeFields(
        form = form,
        onFormChange = onFormChange,
        enabled = enabled,
        amountLabel = stringResource(R.string.contribution_amount),
        descriptionPlaceholder = stringResource(R.string.contribution_placeholder),
    )
    Perforation(Modifier.padding(horizontal = ReceiptPadding))
    Section(stringResource(R.string.charge_who)) {
        MemberChips(form, group, onFormChange, enabled)
    }
}

@Composable
private fun ChargeFields(
    form: ChargeForm,
    onFormChange: (ChargeForm) -> Unit,
    enabled: Boolean,
    amountLabel: String,
    descriptionPlaceholder: String?,
) {
    Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SplitTextField(
            value = form.description,
            onValueChange = { onFormChange(form.copy(description = it.take(MAX_DESCRIPTION_LENGTH))) },
            label = stringResource(R.string.field_description),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = descriptionPlaceholder,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        AmountField(
            value = form.amountText,
            onValueChange = { onFormChange(form.copy(amountText = it)) },
            label = amountLabel,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            figure = true,
            imeAction = ImeAction.Done,
        )
        DateField(
            date = form.date,
            onDateChange = { onFormChange(form.copy(date = it)) },
            label = stringResource(R.string.field_date),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

/** The team members as chips in their highlighter; the pot cannot be charged and is left out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChips(form: ChargeForm, group: GroupDetail, onFormChange: (ChargeForm) -> Unit, enabled: Boolean) {
    val colors = SplitTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        group.players.forEach { member ->
            PersonChip(
                name = member.name,
                selected = member.id in form.memberIds,
                onSelectedChange = { onFormChange(form.toggleMember(member.id)) },
                highlighter = colors.memberHighlighter(group.memberPosition(member.id)),
                enabled = enabled,
            )
        }
    }
}

/** "5 × € 25,00 = € 125,00", once there is an amount and someone to charge. */
@Composable
private fun ChargeTotal(form: ChargeForm) {
    val amountCents = form.amountCents ?: return
    val totalCents = form.totalCents ?: return
    Perforation(Modifier.padding(horizontal = ReceiptPadding))
    Text(
        text = stringResource(
            R.string.charge_total,
            form.memberIds.size,
            formatCents(amountCents),
            formatCents(totalCents),
        ),
        modifier = Modifier
            .padding(start = ReceiptPadding, end = ReceiptPadding, top = 14.dp, bottom = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        content()
    }
}
