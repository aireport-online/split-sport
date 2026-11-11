package online.aireport.split.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatAmountInput
import online.aireport.split.core.parseAmount
import online.aireport.split.data.model.Fine
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.PaperDialog
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.theme.SplitTheme

/** The fine list (boetelijst): what earns a fine and how much; lines can be added, changed and removed. */
@Composable
internal fun FineListReceipt(group: GroupDetail, enabled: Boolean, actions: GroupSettingsActions) {
    var addDialogOpen by rememberSaveable { mutableStateOf(false) }
    var editingFineId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingFineId by rememberSaveable { mutableStateOf<String?>(null) }

    Receipt(Modifier.fillMaxWidth()) {
        SectionTitle(
            text = stringResource(R.string.fine_list),
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = ReceiptPadding, bottom = 6.dp),
        )
        if (group.fines.isEmpty()) {
            Text(
                text = stringResource(R.string.fine_list_empty),
                modifier = Modifier.padding(horizontal = ReceiptPadding, vertical = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitTheme.colors.mutedInk,
            )
        }
        group.fines.forEach { fine ->
            FineRow(fine, enabled, onEdit = { editingFineId = fine.id }, onRemove = { removingFineId = fine.id })
        }
        SecondaryButton(
            text = stringResource(R.string.fine_list_add),
            onClick = { addDialogOpen = true },
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 6.dp, bottom = 12.dp),
            enabled = enabled,
            small = true,
        )
    }

    if (addDialogOpen) {
        FineDialog(
            title = stringResource(R.string.fine_list_add),
            confirmLabel = stringResource(R.string.action_add),
            fine = null,
            onConfirm = { name, amountCents ->
                addDialogOpen = false
                actions.addFine(name, amountCents)
            },
            onDismiss = { addDialogOpen = false },
        )
    }
    group.fines.firstOrNull { it.id == editingFineId }?.let { fine ->
        FineDialog(
            title = stringResource(R.string.fine_list_change),
            confirmLabel = stringResource(R.string.action_save),
            fine = fine,
            onConfirm = { name, amountCents ->
                editingFineId = null
                actions.updateFine(fine.id, name, amountCents)
            },
            onDismiss = { editingFineId = null },
        )
    }
    group.fines.firstOrNull { it.id == removingFineId }?.let { fine ->
        ConfirmDialog(
            title = stringResource(R.string.fine_list_remove_title, fine.name),
            text = stringResource(R.string.fine_list_remove_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                removingFineId = null
                actions.removeFine(fine.id)
            },
            onDismiss = { removingFineId = null },
        )
    }
}

@Composable
private fun FineRow(fine: Fine, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = ReceiptPadding, end = ReceiptPadding - 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReceiptLine(name = fine.name, amount = formatAmount(fine.amountCents), modifier = Modifier.weight(1f))
        IconActionButton(
            icon = painterResource(R.drawable.ic_edit),
            contentDescription = stringResource(R.string.fine_list_change_named, fine.name),
            onClick = onEdit,
            enabled = enabled,
        )
        IconActionButton(
            icon = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.fine_list_remove_named, fine.name),
            onClick = onRemove,
            enabled = enabled,
        )
    }
}

/** Asks for the name and the amount of a line of the fine list; [fine] is the line being changed. */
@Composable
private fun FineDialog(
    title: String,
    confirmLabel: String,
    fine: Fine?,
    onConfirm: (name: String, amountCents: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(fine?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(fine?.amountCents?.let(::formatAmountInput).orEmpty()) }
    val amountCents = parseAmount(amount)?.takeIf { it > 0 }
    val canConfirm = name.isNotBlank() && amountCents != null

    PaperDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = { if (amountCents != null && canConfirm) onConfirm(name, amountCents) },
        onDismiss = onDismiss,
        confirmEnabled = canConfirm,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SplitTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = stringResource(R.string.field_description),
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.fine_list_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            AmountField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.field_amount),
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Done,
            )
        }
    }
}
