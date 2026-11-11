package online.aireport.split.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatSignedCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.components.MemberLabel
import online.aireport.split.ui.components.Perforation
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

/**
 * The team pot: the kasstand, what is still to come in and to go out, who keeps the money, and
 * per team member what they have open with the pot.
 */
@Composable
fun PotTab(group: GroupDetail, onMarkAsPaid: (Transfer) -> Unit, onCopyIban: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "cash") { CashReceipt(group, onCopyIban) }
        item(key = "open") {
            Receipt(Modifier.fillMaxWidth()) {
                ReceiptTitle(stringResource(R.string.pot_open_heading))
                val transfers = group.potTransfers
                if (transfers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pot_nothing_open),
                        modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SplitTheme.colors.mutedInk,
                    )
                }
                transfers.forEachIndexed { index, transfer ->
                    if (index > 0) Perforation(Modifier.padding(horizontal = ReceiptPadding))
                    // The pot has no IBAN of its own: its money goes to the penningmeester above.
                    TransferRow(transfer, group, showReceiverIban = false, onMarkAsPaid, onCopyIban)
                }
            }
        }
    }
}

@Composable
private fun CashReceipt(group: GroupDetail, onCopyIban: (String) -> Unit) {
    val muted = SplitTheme.colors.mutedInk
    val cash = group.pot.cashCents
    Receipt(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(start = ReceiptPadding, end = ReceiptPadding, top = ReceiptPadding, bottom = 14.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.pot_cash), style = MaterialTheme.typography.bodyLarge)
            Text(
                // An amount, not a balance: only an overdrawn pot gets a sign.
                text = if (cash < 0) formatSignedCents(cash) else formatCents(cash),
                style = FigureTextStyle,
            )
        }
        Column(
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ReceiptLine(stringResource(R.string.pot_to_receive), formatAmount(group.pot.toReceiveCents))
            ReceiptLine(stringResource(R.string.pot_to_pay), formatAmount(group.pot.toPayCents))
        }
        Perforation(Modifier.padding(horizontal = ReceiptPadding))
        Column(Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 14.dp, bottom = 6.dp)) {
            Text(stringResource(R.string.treasurer), style = MaterialTheme.typography.bodyMedium, color = muted)
            val treasurer = group.treasurer
            if (treasurer == null) {
                Text(
                    text = stringResource(R.string.treasurer_none),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = muted,
                )
            } else {
                MemberLabel(
                    member = treasurer,
                    position = group.memberPosition(treasurer.id),
                    myMemberId = group.myMemberId,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (treasurer.iban != null) {
                    IbanLine(treasurer.iban, onCopyIban)
                } else {
                    Text(
                        text = stringResource(R.string.treasurer_no_iban),
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
            }
        }
    }
}
