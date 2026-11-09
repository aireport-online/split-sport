package online.aireport.split.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import online.aireport.split.ui.theme.SplitTheme

private val LeaderGap = 8.dp
private val LeaderMinWidth = 16.dp
private val LeaderDot = 2.dp
private val LeaderSpacing = 6.dp

/**
 * A receipt line, "name ........ amount": the amount keeps its width at the end, the name gets
 * the rest (one line, cut off with an ellipsis), and a dotted leader on the text baseline fills
 * what is left between them.
 */
@Composable
fun ReceiptLine(
    modifier: Modifier = Modifier,
    leader: Boolean = true,
    name: @Composable () -> Unit,
    amount: @Composable () -> Unit,
) {
    val leaderColor = if (leader) SplitTheme.colors.perforation else Color.Transparent
    Layout(
        contents = listOf(name, amount, { Spacer(Modifier.leaderDots(leaderColor)) }),
        modifier = modifier,
    ) { (names, amounts, leaders), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = LeaderGap.roundToPx()
        val amountPlaceable = amounts.first().measure(loose)
        val nameMaxWidth = (constraints.maxWidth - amountPlaceable.width - 2 * gap - LeaderMinWidth.roundToPx())
            .coerceAtLeast(0)
        val namePlaceable = names.first().measure(loose.copy(maxWidth = nameMaxWidth))
        val leaderWidth = (constraints.maxWidth - namePlaceable.width - amountPlaceable.width - 2 * gap)
            .coerceAtLeast(0)
        val leaderPlaceable = leaders.first().measure(Constraints.fixed(leaderWidth, LeaderDot.roundToPx()))

        val baseline = maxOf(namePlaceable.baseline(), amountPlaceable.baseline())
        val height = maxOf(
            baseline + namePlaceable.height - namePlaceable.baseline(),
            baseline + amountPlaceable.height - amountPlaceable.baseline(),
        ).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(constraints.maxWidth, height) {
            namePlaceable.placeRelative(0, baseline - namePlaceable.baseline())
            leaderPlaceable.placeRelative(namePlaceable.width + gap, baseline - leaderPlaceable.height)
            amountPlaceable.placeRelative(
                constraints.maxWidth - amountPlaceable.width,
                baseline - amountPlaceable.baseline(),
            )
        }
    }
}

/** A receipt line of plain text. */
@Composable
fun ReceiptLine(
    name: String,
    amount: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
) {
    ReceiptLine(
        modifier = modifier,
        name = { Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        amount = { Text(amount, style = style, color = color, maxLines = 1) },
    )
}

private fun Placeable.baseline(): Int = this[FirstBaseline].takeIf { it >= 0 } ?: height

/** Dots of 2 dp, 6 dp apart, counted from the amount's side so the leaders of a column line up. */
private fun Modifier.leaderDots(color: Color): Modifier = drawBehind {
    val radius = LeaderDot.toPx() / 2
    val spacing = LeaderSpacing.toPx()
    var x = size.width - radius
    while (x >= radius) {
        drawCircle(color, radius, Offset(x, size.height - radius))
        x -= spacing
    }
}
