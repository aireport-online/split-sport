package online.aireport.split.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.ui.theme.SplitTheme

/**
 * The colour of a balance. There is no green or red: the sign says it. Only zero differs, in
 * the muted ink.
 */
@Composable
@ReadOnlyComposable
fun balanceColor(balanceCents: Long): Color =
    if (balanceCents == 0L) SplitTheme.colors.mutedInk else SplitTheme.colors.ink

/** Your own balance as a sentence: "Je krijgt € 12,50", "Je bent € 8,00 verschuldigd", "Je staat quitte". */
@Composable
@ReadOnlyComposable
fun ownBalanceSentence(balanceCents: Long): String = when {
    balanceCents > 0 -> stringResource(R.string.balance_own_positive, formatCents(balanceCents))
    balanceCents < 0 -> stringResource(R.string.balance_own_negative, formatCents(abs(balanceCents)))
    else -> stringResource(R.string.balance_own_zero)
}

/** Someone's balance after their name: "krijgt € 12,50", "betaalt € 8,00", "quitte". */
@Composable
@ReadOnlyComposable
fun memberBalancePhrase(balanceCents: Long): String = when {
    balanceCents > 0 -> stringResource(R.string.balance_member_positive, formatCents(balanceCents))
    balanceCents < 0 -> stringResource(R.string.balance_member_negative, formatCents(abs(balanceCents)))
    else -> stringResource(R.string.balance_member_zero)
}
