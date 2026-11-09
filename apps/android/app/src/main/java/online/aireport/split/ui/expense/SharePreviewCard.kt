package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.memberLabel

/** The live "Per persoon" summary: what everyone owes, or why the split is not valid yet. */
@Composable
fun SharePreviewCard(preview: SplitPreview, group: GroupDetail, modifier: Modifier = Modifier) {
    SplitCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.split_per_person),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            when (preview) {
                is SplitPreview.Invalid -> Text(
                    text = stringResource(preview.error.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is SplitPreview.Valid -> preview.shares.forEach { share ->
                    val member = group.member(share.memberId)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = member?.let { memberLabel(it, group.myMemberId) }
                                ?: stringResource(R.string.member_unknown),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(text = formatCents(share.amountCents), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
