package online.aireport.split.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatIban
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.components.EmptyState
import online.aireport.split.ui.components.MemberAvatar
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.balanceColor
import online.aireport.split.ui.components.memberBalancePhrase
import online.aireport.split.ui.components.memberLabel

/** Every member's balance, and the payments that would settle the group. */
@Composable
fun BalancesTab(group: GroupDetail, onMarkAsPaid: (Transfer) -> Unit, onCopyIban: (String) -> Unit) {
    val balances = group.balances.associate { it.memberId to it.balanceCents }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "balances") {
            SplitCard {
                group.members.forEachIndexed { position, member ->
                    if (position > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MemberBalanceRow(member, position, group.myMemberId, balances[member.id] ?: 0L)
                }
            }
        }
        item(key = "settle-heading") {
            Text(
                text = stringResource(R.string.settle_heading),
                modifier = Modifier
                    .padding(start = 4.dp, top = 12.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (group.transfers.isEmpty()) {
            item(key = "settled") { EmptyState(stringResource(R.string.settle_everyone_square)) }
        } else {
            items(group.transfers, key = { "${it.fromMemberId}-${it.toMemberId}" }) { transfer ->
                TransferCard(transfer, group, onMarkAsPaid, onCopyIban)
            }
        }
    }
}

@Composable
private fun MemberBalanceRow(member: Member, position: Int, myMemberId: String, balanceCents: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member.name, position)
        Column(Modifier.weight(1f)) {
            Text(
                text = memberLabel(member, myMemberId),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.userId == null) {
                Text(
                    text = stringResource(R.string.member_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = memberBalancePhrase(balanceCents),
            style = MaterialTheme.typography.bodyLarge,
            color = balanceColor(balanceCents),
        )
    }
}

@Composable
private fun TransferCard(
    transfer: Transfer,
    group: GroupDetail,
    onMarkAsPaid: (Transfer) -> Unit,
    onCopyIban: (String) -> Unit,
) {
    val receiverIban = group.member(transfer.toMemberId)?.iban
    SplitCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.settle_transfer,
                    group.memberName(transfer.fromMemberId),
                    group.memberName(transfer.toMemberId),
                    formatCents(transfer.amountCents),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (receiverIban != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.iban_value, formatIban(receiverIban)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onCopyIban(receiverIban) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.iban_copy),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            SecondaryButton(stringResource(R.string.settle_mark_paid), onClick = { onMarkAsPaid(transfer) })
        }
    }
}
