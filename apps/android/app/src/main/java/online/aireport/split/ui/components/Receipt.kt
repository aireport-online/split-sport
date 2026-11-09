package online.aireport.split.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import online.aireport.split.ui.theme.SplitTheme

private val ToothWidth = 12.dp
private val ToothHeight = 6.dp
private val TopCornerRadius = 2.dp

/**
 * A piece of receipt paper: a zigzag tear at the bottom, with teeth 12 dp wide and 6 dp high
 * (stretched a fraction so a whole number fits), and with [roundedTop] barely rounded top corners.
 */
class ReceiptShape(private val roundedTop: Boolean = true) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = if (roundedTop) with(density) { TopCornerRadius.toPx() } else 0f
        val toothHeight = with(density) { ToothHeight.toPx() }
        val teeth = (size.width / with(density) { ToothWidth.toPx() }).roundToInt().coerceAtLeast(1)
        val toothWidth = size.width / teeth
        val paperBottom = size.height - toothHeight
        val path = Path().apply {
            moveTo(0f, corner)
            quadraticTo(0f, 0f, corner, 0f)
            lineTo(size.width - corner, 0f)
            quadraticTo(size.width, 0f, size.width, corner)
            lineTo(size.width, paperBottom)
            for (tooth in teeth - 1 downTo 0) {
                lineTo((tooth + 0.5f) * toothWidth, size.height)
                lineTo(tooth * toothWidth, paperBottom)
            }
            close()
        }
        return Outline.Generic(path)
    }
}

private val WholeReceipt = ReceiptShape()
private val ReceiptEnd = ReceiptShape(roundedTop = false)
private val ReceiptStart = RoundedCornerShape(topStart = TopCornerRadius, topEnd = TopCornerRadius)

/**
 * A receipt: paper on the desk, without border or shadow. Like a real one it ends with a
 * perforation just above the tear. With [onClick] the whole receipt can be tapped.
 */
@Composable
fun Receipt(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SplitTheme.colors
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        Column(
            modifier = modifier
                .clip(WholeReceipt)
                .background(colors.paper)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(bottom = ToothHeight),
        ) {
            content()
            Perforation(Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = TearMargin))
        }
    }
}

/**
 * The top edge of a receipt whose lines are items of a lazy list: put this above the first
 * line, give every line the paper colour, and end with [ReceiptBottom].
 */
@Composable
fun ReceiptTop(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(TearMargin)
            .background(SplitTheme.colors.paper, ReceiptStart),
    )
}

/** The perforation and the tear that end a receipt built from lazy list items. */
@Composable
fun ReceiptBottom(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ReceiptEnd)
            .background(SplitTheme.colors.paper)
            .padding(top = 4.dp, bottom = ToothHeight),
    ) {
        Perforation(Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, bottom = TearMargin))
    }
}

private val TearMargin = 10.dp

/** The padding of content on a receipt. */
val ReceiptPadding = 18.dp

/** A dashed tear line between two parts of one receipt. */
@Composable
fun Perforation(modifier: Modifier = Modifier) {
    val color = SplitTheme.colors.perforation
    Canvas(
        modifier
            .fillMaxWidth()
            .height(PerforationWidth),
    ) {
        drawPerforation(color, y = size.height / 2)
    }
}

internal val PerforationWidth = 1.5.dp

/** Draws a perforation across the full width at [y]: a 1.5 dp line with dashes of 6 and gaps of 4. */
internal fun DrawScope.drawPerforation(color: Color, y: Float) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = PerforationWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}
