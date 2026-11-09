package online.aireport.split.ui.expense

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.QuietButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.memberLabel

private const val MAX_ITEM_NAME_LENGTH = 120

/** "Per regel": the lines of the receipt, each with a name, an amount and the members as toggles. */
@Composable
fun ItemsSplitEditor(
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
    scanEnabled: Boolean,
    isScanning: Boolean,
    newPhotoUri: () -> Uri,
    onReceiptPhoto: (Uri) -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scanEnabled) {
            ReceiptScanButton(
                isScanning = isScanning,
                enabled = enabled,
                newPhotoUri = newPhotoUri,
                onReceiptPhoto = onReceiptPhoto,
                onCameraUnavailable = onCameraUnavailable,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (draft.items.isEmpty()) {
            Text(
                text = stringResource(R.string.split_items_empty),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        draft.items.forEach { item ->
            key(item.key) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ItemEditor(item, group, draft, onDraftChange, enabled)
            }
        }
        if (draft.items.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        QuietButton(
            text = stringResource(R.string.split_items_add),
            onClick = { onDraftChange(draft.addItem(group.members.map { it.id })) },
            modifier = Modifier.padding(horizontal = 8.dp),
            enabled = enabled,
        )

        val difference = draft.itemsDifferenceCents
        if (difference != 0L) {
            Text(
                text = stringResource(R.string.split_items_difference, formatCents(abs(difference))),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemEditor(
    item: ItemDraft,
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Name and amount each get a full row so both stay readable at a large font size.
        SplitTextField(
            value = item.name,
            onValueChange = { name ->
                onDraftChange(draft.updateItem(item.key) { it.copy(name = name.take(MAX_ITEM_NAME_LENGTH)) })
            },
            label = stringResource(R.string.field_item_name),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountField(
                value = item.amountText,
                onValueChange = { text -> onDraftChange(draft.updateItem(item.key) { it.copy(amountText = text) }) },
                label = stringResource(R.string.field_amount),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                imeAction = ImeAction.Done,
            )
            IconButton(onClick = { onDraftChange(draft.removeItem(item.key)) }, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.split_items_remove, item.name),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.members.forEach { member ->
                FilterChip(
                    selected = member.id in item.memberIds,
                    onClick = { onDraftChange(draft.toggleItemMember(item.key, member.id)) },
                    label = { Text(memberLabel(member, group.myMemberId)) },
                    enabled = enabled,
                )
            }
        }
    }
}
