package online.aireport.split.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatIban
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.components.MemberName
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.theme.SplitTheme

/** The title of a receipt on the team screen. */
@Composable
internal fun ReceiptTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = ReceiptPadding, end = ReceiptPadding, top = 16.dp, bottom = 6.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}

/**
 * A suggested payment, "Bas → Anna 3,35" or "Bas → Teamkas 12,00", with "Markeer als betaald".
 * With [showReceiverIban] the receiver's IBAN is shown when it is known.
 */
@Composable
internal fun TransferRow(
    transfer: Transfer,
    group: GroupDetail,
    showReceiverIban: Boolean,
    onMarkAsPaid: (Transfer) -> Unit,
    onCopyIban: (String) -> Unit,
) {
    val sentence = stringResource(
        R.string.settle_transfer,
        group.memberName(transfer.fromMemberId),
        group.memberName(transfer.toMemberId),
        formatCents(transfer.amountCents),
    )
    val receiverIban = group.member(transfer.toMemberId)?.iban?.takeIf { showReceiverIban }

    Column(Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 14.dp, bottom = 8.dp)) {
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
                MemberName(group, transfer.fromMemberId, Modifier.weight(1f, fill = false))
                Text("→", style = MaterialTheme.typography.bodyLarge)
                MemberName(group, transfer.toMemberId, Modifier.weight(1f, fill = false))
            }
            Text(formatAmount(transfer.amountCents), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        if (receiverIban != null) IbanLine(receiverIban, onCopyIban)
        SecondaryButton(stringResource(R.string.settle_mark_paid), onClick = { onMarkAsPaid(transfer) }, small = true)
    }
}

/** "IBAN NL91 ABNA 0417 1643 00" with a copy icon; the whole line copies the IBAN. */
@Composable
internal fun IbanLine(iban: String, onCopyIban: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = stringResource(R.string.iban_copy), role = Role.Button) { onCopyIban(iban) }
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.iban_value, formatIban(iban)),
            style = MaterialTheme.typography.bodyMedium,
            color = SplitTheme.colors.mutedInk,
        )
        Icon(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = SplitTheme.colors.mutedInk,
        )
    }
}

@Composable
internal fun GroupDetail.memberName(memberId: String?): String =
    memberId?.let(::member)?.name ?: stringResource(R.string.member_unknown)
