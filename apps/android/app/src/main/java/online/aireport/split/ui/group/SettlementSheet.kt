package online.aireport.split.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.DateField
import online.aireport.split.ui.components.MemberDropdown
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.QuietButton

/** The payment sheet: van, aan, bedrag, datum. Saved as a settlement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementSheet(
    form: SettlementForm,
    group: GroupDetail,
    onChange: (SettlementForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settlement_sheet_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            MemberDropdown(
                label = stringResource(R.string.settlement_from),
                members = group.members,
                selectedId = form.fromMemberId,
                onSelect = { onChange(form.copy(fromMemberId = it)) },
                myMemberId = group.myMemberId,
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isSaving,
            )
            MemberDropdown(
                label = stringResource(R.string.settlement_to),
                members = group.members,
                selectedId = form.toMemberId,
                onSelect = { onChange(form.copy(toMemberId = it)) },
                myMemberId = group.myMemberId,
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isSaving,
            )
            AmountField(
                value = form.amountText,
                onValueChange = { onChange(form.copy(amountText = it)) },
                label = stringResource(R.string.field_amount),
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isSaving,
                imeAction = ImeAction.Done,
            )
            DateField(
                date = form.date,
                onDateChange = { onChange(form.copy(date = it)) },
                label = stringResource(R.string.field_date),
                modifier = Modifier.fillMaxWidth(),
                enabled = !form.isSaving,
            )
            val problem = when {
                form.error != null -> form.error.resolve()
                form.fromMemberId == form.toMemberId -> stringResource(R.string.settlement_same_member)
                else -> null
            }
            if (problem != null) {
                Text(
                    text = problem,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = form.canSave,
                loading = form.isSaving,
            )
        }
    }
}

/** A recorded payment between members. It cannot be edited, only removed. */
@Composable
fun SettlementDialog(settlement: Expense, group: GroupDetail, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(expenseTitle(settlement, group)) },
        text = {
            Text(
                stringResource(
                    R.string.settlement_details,
                    formatCents(settlement.amountCents),
                    expenseSubtitle(settlement, group),
                ),
            )
        },
        confirmButton = { QuietButton(stringResource(R.string.action_delete), onDelete, destructive = true) },
        dismissButton = { QuietButton(stringResource(R.string.action_close), onDismiss) },
    )
}
