package online.aireport.split.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import online.aireport.split.R
import online.aireport.split.ui.theme.BarlowCondensed
import online.aireport.split.ui.theme.SplitTheme

/** The rubber stamp on a payment between members: red, outlined and rotated -8°. */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier) {
    val red = SplitTheme.colors.stamp
    Text(
        text = text.uppercase(),
        modifier = modifier
            .rotate(-8f)
            .border(2.dp, red, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        color = red,
        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.04.em),
        maxLines = 1,
    )
}

/**
 * "Split", torn in two: a dashed perforation in ink runs between "Sp" and "lit" and extends a
 * little above and below the letters.
 */
@Composable
fun Wordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    val style = TextStyle(fontFamily = BarlowCondensed, fontSize = fontSize, lineHeight = fontSize)
    val color = LocalContentColor.current
    val em = with(LocalDensity.current) { fontSize.toDp() }
    Row(
        modifier = modifier
            .height(em)
            .clearAndSetSemantics { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.take(2), style = style, color = color)
        Spacer(
            Modifier
                .padding(horizontal = em * 0.07f)
                .width(em * 0.05f)
                .fillMaxHeight()
                .drawBehind {
                    val overshoot = size.height * 0.1f
                    drawLine(
                        color = color,
                        start = Offset(size.width / 2, -overshoot),
                        end = Offset(size.width / 2, size.height + overshoot),
                        strokeWidth = size.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(size.height * 0.13f, size.height * 0.08f)),
                    )
                },
        )
        Text(name.drop(2), style = style, color = color)
    }
}

/**
 * The highlighter stroke under a receipt line, showing who had it: one equal part per person,
 * left to right in member order. Empty when nobody is assigned.
 */
@Composable
fun ItemSwipe(highlighters: List<Color>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(8.dp)
            .rotate(-0.4f),
    ) {
        highlighters.forEach { color ->
            Spacer(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind { drawRect(color) },
            )
        }
    }
}

/** A small dashed-outline label, e.g. "geen account". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    val colors = SplitTheme.colors
    Text(
        text = text,
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = colors.perforation,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = colors.mutedInk,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}
