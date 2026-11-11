package online.aireport.split.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatSignedAmount
import online.aireport.split.data.model.FineStat
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.components.MemberLabel
import online.aireport.split.ui.components.MemberName
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.Pill
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.Stamp
import online.aireport.split.ui.components.balanceColor
import online.aireport.split.ui.components.memberBalancePhrase
import online.aireport.split.ui.theme.SplitTheme

/**
 * What team members have among each other: everyone's balance, under "Vereffenen" the payments
 * between members that would settle it, and the fine ranking.
 */
@Composable
fun BalancesTab(group: GroupDetail, onMarkAsPaid: (Transfer) -> Unit, onCopyIban: (String) -> Unit) {
    val peerBalances = group.balances.associate { it.memberId to it.peerCents }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balances") {
            Receipt(Modifier.fillMaxWidth()) {
                ReceiptTitle(stringResource(R.string.group_tab_balances))
                group.players.forEach { member ->
                    MemberBalanceRow(member, group.memberPosition(member.id), group.myMemberId, peerBalances[member.id] ?: 0L)
                }
            }
        }
        item(key = "settle") {
            Receipt(Modifier.fillMaxWidth()) {
                ReceiptTitle(stringResource(R.string.settle_heading))
                val transfers = group.peerTransfers
                if (transfers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settle_everyone_square),
                        modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitTheme.colors.mutedInk,
                    )
                }
                transfers.forEachIndexed { index, transfer ->
                    if (index > 0) Perforation(Modifier.padding(horizontal = ReceiptPadding))
                    TransferRow(transfer, group, showReceiverIban = true, onMarkAsPaid, onCopyIban)
                }
            }
        }
        if (group.fineStats.isNotEmpty()) {
            item(key = "fines") {
                Receipt(Modifier.fillMaxWidth()) {
                    ReceiptTitle(stringResource(R.string.fine_king_heading))
                    group.fineStats.forEachIndexed { rank, stat -> FineKingRow(stat, group, isKing = rank == 0) }
                }
            }
        }
    }
}

@Composable
private fun MemberBalanceRow(member: Member, position: Int, myMemberId: String, balanceCents: Long) {
    val balanceDescription = memberBalancePhrase(balanceCents)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = ReceiptPadding)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberLabel(member, position, myMemberId, Modifier.weight(1f, fill = false))
            if (member.userId == null) Pill(stringResource(R.string.member_no_account))
        }
        Text(
            text = formatSignedAmount(balanceCents),
            // Spoken as "krijgt € 24,95" rather than "plus 24,95".
            modifier = Modifier.semantics { contentDescription = balanceDescription },
            style = MaterialTheme.typography.bodyLarge,
            color = balanceColor(balanceCents),
            maxLines = 1,
        )
    }
}

/** A line of the fine ranking: the name, "3×" and the total; the first one is stamped "KONING". */
@Composable
private fun FineKingRow(stat: FineStat, group: GroupDetail, isKing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = ReceiptPadding)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberName(group, stat.memberId, Modifier.weight(1f, fill = false))
            if (isKing) Stamp(stringResource(R.string.stamp_king))
        }
        Text(
            text = stringResource(R.string.fine_count, stat.count),
            style = MaterialTheme.typography.bodyLarge,
            color = SplitTheme.colors.mutedInk,
            maxLines = 1,
        )
        Text(
            text = formatAmount(stat.totalCents),
            modifier = Modifier.widthIn(min = 56.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
