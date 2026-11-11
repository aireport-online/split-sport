package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.ItemSwipe
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.PersonChip
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.theme.SplitTheme

private const val MAX_ITEM_NAME_LENGTH = 120

/**
 * "Per regel": the lines of the receipt, each with a name, an amount and the members as chips.
 * Under every line a highlighter stroke shows who had it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemsSplitEditor(
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
    scanEnabled: Boolean,
    isScanning: Boolean,
    receiptActions: ReceiptActions,
) {
    Column {
        if (draft.items.isEmpty()) {
            Text(
                text = stringResource(R.string.split_items_empty),
                modifier = Modifier.padding(horizontal = ReceiptPadding, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
            )
        }
        draft.items.forEachIndexed { index, item ->
            key(item.key) {
                if (index > 0) Perforation(Modifier.padding(horizontal = ReceiptPadding, vertical = 4.dp))
                ItemEditor(item, group, draft, onDraftChange, enabled)
            }
        }

        FlowRow(
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SecondaryButton(
                text = stringResource(R.string.split_items_add),
                onClick = { onDraftChange(draft.addItem(group.players.map { it.id })) },
                enabled = enabled,
                small = true,
            )
            if (scanEnabled) ReceiptScanButton(isScanning, enabled, receiptActions)
        }

        val difference = draft.itemsDifferenceCents
        if (difference != 0L) {
            Text(
                text = stringResource(R.string.split_items_difference, formatCents(abs(difference))),
                modifier = Modifier
                    .padding(start = ReceiptPadding, end = ReceiptPadding, top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
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
    val colors = SplitTheme.colors
    Column(Modifier.padding(start = ReceiptPadding, end = ReceiptPadding - 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SplitTextField(
                value = item.name,
                onValueChange = { name ->
                    onDraftChange(draft.updateItem(item.key) { it.copy(name = name.take(MAX_ITEM_NAME_LENGTH)) })
                },
                label = stringResource(R.string.field_item_name),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                showLabel = false,
                placeholder = stringResource(R.string.field_item_name),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            AmountField(
                value = item.amountText,
                onValueChange = { text -> onDraftChange(draft.updateItem(item.key) { it.copy(amountText = text) }) },
                label = stringResource(R.string.split_items_amount_of, item.name),
                modifier = Modifier.width(88.dp),
                enabled = enabled,
                showLabel = false,
                alignEnd = true,
                placeholder = stringResource(R.string.amount_placeholder),
                imeAction = ImeAction.Done,
            )
            IconActionButton(
                icon = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.split_items_remove, item.name),
                onClick = { onDraftChange(draft.removeItem(item.key)) },
                enabled = enabled,
            )
        }
        // A person has their highlighter; the pot, which is not a person, has ink.
        val strokes = group.members.mapIndexed { position, member ->
            if (member.isPot) colors.ink else colors.memberHighlighter(position)
        }
        ItemSwipe(
            highlighters = group.members.indices.filter { group.members[it].id in item.memberIds }.map(strokes::get),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, end = 54.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            group.members.forEachIndexed { position, member ->
                PersonChip(
                    name = member.name,
                    selected = member.id in item.memberIds,
                    onSelectedChange = { onDraftChange(draft.toggleItemMember(item.key, member.id)) },
                    highlighter = strokes[position].takeUnless { member.isPot },
                    enabled = enabled,
                )
            }
        }
    }
}
