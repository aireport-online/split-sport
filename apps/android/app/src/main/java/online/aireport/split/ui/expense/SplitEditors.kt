package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.MAX_WEIGHT
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.MemberAvatar
import online.aireport.split.ui.components.memberLabel

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
    Column {
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
                    .heightIn(min = 56.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The row is the toggle; the checkbox only shows the state.
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp), enabled = enabled)
                MemberName(member, position, group.myMemberId, Modifier.weight(1f))
                Text(
                    text = shares[member.id]?.let(::formatCents) ?: stringResource(R.string.amount_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.members.forEachIndexed { position, member ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberName(member, position, group.myMemberId, Modifier.weight(1f))
                AmountField(
                    value = draft.exactAmounts[member.id].orEmpty(),
                    onValueChange = { onDraftChange(draft.setExactAmount(member.id, it)) },
                    label = stringResource(R.string.field_amount),
                    modifier = Modifier.width(140.dp),
                    enabled = enabled,
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
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .heightIn(min = 56.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberName(member, position, group.myMemberId, Modifier.weight(1f))
                IconButton(
                    onClick = { onDraftChange(draft.changeWeight(member.id, -1)) },
                    enabled = enabled && weight > 0,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        stringResource(R.string.split_shares_less, member.name),
                    )
                }
                Text(
                    text = weight.toString(),
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = if (weight > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onDraftChange(draft.changeWeight(member.id, 1)) },
                    enabled = enabled && weight < MAX_WEIGHT,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        stringResource(R.string.split_shares_more, member.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberName(member: Member, position: Int, myMemberId: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member.name, position, size = 32.dp)
        Text(
            text = memberLabel(member, myMemberId),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
