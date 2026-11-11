package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatAmount
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.MemberLabel
import online.aireport.split.ui.components.MemberName
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.theme.SplitTheme

/**
 * The live "Per persoon" summary at the foot of the receipt: what everyone owes and the total,
 * or why the split is not valid yet.
 */
@Composable
fun SharePreview(preview: SplitPreview, group: GroupDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.split_per_person),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        when (preview) {
            is SplitPreview.Invalid -> Text(
                text = stringResource(preview.error.messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = SplitTheme.colors.mutedInk,
            )
            is SplitPreview.Valid -> {
                preview.shares.forEach { share ->
                    ReceiptLine(
                        name = {
                            val member = group.member(share.memberId)
                            if (member != null) {
                                MemberLabel(member, group.memberPosition(member.id), group.myMemberId)
                            } else {
                                MemberName(group, memberId = null)
                            }
                        },
                        amount = { Text(formatAmount(share.amountCents), style = MaterialTheme.typography.bodyLarge) },
                    )
                }
                val total = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                ReceiptLine(
                    modifier = Modifier.padding(top = 6.dp),
                    leader = false,
                    name = { Text(stringResource(R.string.expense_total), style = total) },
                    amount = { Text(formatCents(preview.amountCents), style = total) },
                )
            }
        }
    }
}
