package online.aireport.split.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlin.math.abs
import online.aireport.split.R
import online.aireport.split.core.formatCents
import online.aireport.split.ui.theme.SplitTheme

/**
 * The colour of a balance: positive in the positive colour, negative in the normal text colour
 * (owing money is not an error), zero in grey.
 */
@Composable
@ReadOnlyComposable
fun balanceColor(balanceCents: Long): Color = when {
    balanceCents > 0 -> SplitTheme.colors.positive
    balanceCents < 0 -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

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

/** A balance as a signed amount: "+ € 12,50", "− € 8,00", and "quitte" for zero. */
@Composable
@ReadOnlyComposable
fun signedBalance(balanceCents: Long): String = when {
    balanceCents > 0 -> stringResource(R.string.balance_signed_positive, formatCents(balanceCents))
    balanceCents < 0 -> stringResource(R.string.balance_signed_negative, formatCents(abs(balanceCents)))
    else -> stringResource(R.string.balance_member_zero)
}
