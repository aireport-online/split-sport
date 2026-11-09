package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.MAX_WEIGHT
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.DISABLED_ALPHA
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.InkCheckbox
import online.aireport.split.ui.components.PersonLabel
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.theme.SplitTheme

/** "Gelijk": tick who takes part; every row shows that person's amount. */
@Composable
fun EqualSplitEditor(
    group: GroupDetail,
    draft: ExpenseDraft,
    preview: SplitPreview?,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
) {
    val shares = (preview as? SplitPreview.Valid)?.shares.orEmpty().associate { it.memberId to it.amountCents }
    Column(Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        group.members.forEachIndexed { position, member ->
            val checked = member.id in draft.equalMemberIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onDraftChange(draft.toggleEqualMember(member.id)) },
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = ReceiptPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { PersonLabel(member, position, group.myMemberId) }
                InkCheckbox(checked)
                Text(
                    text = shares[member.id]?.let(::formatAmount) ?: stringResource(R.string.amount_none),
                    modifier = Modifier.widthIn(min = 72.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) SplitTheme.colors.ink else SplitTheme.colors.mutedInk,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "Bedragen": an amount per person, and how much is still to divide. */
@Composable
fun ExactSplitEditor(
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
) {
    Column(Modifier.padding(horizontal = ReceiptPadding)) {
        group.members.forEachIndexed { position, member ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { PersonLabel(member, position, group.myMemberId) }
                AmountField(
                    value = draft.exactAmounts[member.id].orEmpty(),
                    onValueChange = { onDraftChange(draft.setExactAmount(member.id, it)) },
                    label = stringResource(R.string.split_exact_amount_for, member.name),
                    modifier = Modifier.width(112.dp),
                    enabled = enabled,
                    showLabel = false,
                    alignEnd = true,
                    placeholder = stringResource(R.string.amount_placeholder),
                )
            }
        }
        val remaining = draft.exactRemainingCents
        if (remaining != null && remaining != 0L) {
            Text(
                text = stringResource(
                    if (remaining > 0) R.string.split_exact_remaining else R.string.split_exact_over,
                    formatCents(abs(remaining)),
                ),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
            )
        }
    }
}

/** "Aandelen": a stepper per person; 0 means not in. */
@Composable
fun SharesSplitEditor(
    group: GroupDetail,
    draft: ExpenseDraft,
    onDraftChange: (ExpenseDraft) -> Unit,
    enabled: Boolean,
) {
    Column {
        group.members.forEachIndexed { position, member ->
            val weight = draft.weights[member.id] ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = ReceiptPadding, end = ReceiptPadding - 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { PersonLabel(member, position, group.myMemberId) }
                IconActionButton(
                    icon = painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.split_shares_less, member.name),
                    onClick = { onDraftChange(draft.changeWeight(member.id, -1)) },
                    enabled = enabled && weight > 0,
                    outlined = true,
                )
                Text(
                    text = weight.toString(),
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (weight > 0) SplitTheme.colors.ink else SplitTheme.colors.mutedInk,
                )
                IconActionButton(
                    icon = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.split_shares_more, member.name),
                    onClick = { onDraftChange(draft.changeWeight(member.id, 1)) },
                    enabled = enabled && weight < MAX_WEIGHT,
                    outlined = true,
                )
            }
        }
    }
}
