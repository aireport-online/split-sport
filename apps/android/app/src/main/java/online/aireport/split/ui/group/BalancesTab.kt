package online.aireport.split.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatIban
import online.aireport.split.core.formatSignedAmount
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.PersonLabel
import online.aireport.split.ui.components.PersonName
import online.aireport.split.ui.components.Pill
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.balanceColor
import online.aireport.split.ui.components.memberBalancePhrase
import online.aireport.split.ui.theme.SplitTheme

/** Every member's balance, and under "Vereffenen" the payments that would settle the group. */
@Composable
fun BalancesTab(group: GroupDetail, onMarkAsPaid: (Transfer) -> Unit, onCopyIban: (String) -> Unit) {
    val balances = group.balances.associate { it.memberId to it.balanceCents }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balances") {
            Receipt(Modifier.fillMaxWidth()) {
                ReceiptTitle(stringResource(R.string.group_tab_balances))
                group.members.forEachIndexed { position, member ->
                    MemberBalanceRow(member, position, group.myMemberId, balances[member.id] ?: 0L)
                }
            }
        }
        item(key = "settle") {
            Receipt(Modifier.fillMaxWidth()) {
                ReceiptTitle(stringResource(R.string.settle_heading))
                if (group.transfers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settle_everyone_square),
                        modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitTheme.colors.mutedInk,
                    )
                }
                group.transfers.forEachIndexed { index, transfer ->
                    if (index > 0) Perforation(Modifier.padding(horizontal = ReceiptPadding))
                    TransferRow(transfer, group, onMarkAsPaid, onCopyIban)
                }
            }
        }
    }
}

@Composable
private fun ReceiptTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = ReceiptPadding, end = ReceiptPadding, top = 16.dp, bottom = 6.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
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
            PersonLabel(member, position, myMemberId, Modifier.weight(1f, fill = false))
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

@Composable
private fun TransferRow(
    transfer: Transfer,
    group: GroupDetail,
    onMarkAsPaid: (Transfer) -> Unit,
    onCopyIban: (String) -> Unit,
) {
    val fromName = group.memberName(transfer.fromMemberId)
    val toName = group.memberName(transfer.toMemberId)
    val sentence = stringResource(R.string.settle_transfer, fromName, toName, formatCents(transfer.amountCents))
    val receiverIban = group.member(transfer.toMemberId)?.iban

    Column(
        modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            // Spoken as the sentence "Bas betaalt Anna € 3,33".
            modifier = Modifier.clearAndSetSemantics { contentDescription = sentence },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonName(fromName, group.memberPosition(transfer.fromMemberId), Modifier.weight(1f, fill = false))
                Text("→", style = MaterialTheme.typography.bodyLarge)
                PersonName(toName, group.memberPosition(transfer.toMemberId), Modifier.weight(1f, fill = false))
            }
            Text(formatAmount(transfer.amountCents), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        if (receiverIban != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.iban_value, formatIban(receiverIban)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitTheme.colors.mutedInk,
                )
                IconActionButton(
                    icon = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.iban_copy),
                    onClick = { onCopyIban(receiverIban) },
                )
            }
        }
        SecondaryButton(stringResource(R.string.settle_mark_paid), onClick = { onMarkAsPaid(transfer) }, small = true)
    }
}
