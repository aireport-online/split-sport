package online.aireport.split.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import online.aireport.split.ui.theme.SplitTheme

/**
 * The check box of the house style: a 20 dp square with a 1.5 dp ink outline, filled with ink
 * when checked. It only shows the state; the row it is in is the control.
 */
@Composable
fun InkCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = SplitTheme.colors
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .size(20.dp)
            .background(if (checked) colors.ink else Color.Transparent, shape)
            .border(1.5.dp, colors.ink, shape)
            .drawBehind {
                if (!checked) return@drawBehind
                // The check mark of a 16 px grid: (3.5, 8.5) -> (6.5, 11.5) -> (12.5, 4.5).
                val unit = size.minDimension / 16f
                val check = Path().apply {
                    moveTo(3.5f * unit, 8.5f * unit)
                    lineTo(6.5f * unit, 11.5f * unit)
                    lineTo(12.5f * unit, 4.5f * unit)
                }
                drawPath(check, colors.onInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
            },
    )
}

/**
 * Says who had a receipt line (or picks one of a few options): the name on a filled pill when
 * selected, in [highlighter] or, without one, in ink; a dashed outline and muted text when not.
 */
@Composable
fun PersonChip(
    name: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    highlighter: Color? = null,
    enabled: Boolean = true,
    role: Role = Role.Checkbox,
) {
    val colors = SplitTheme.colors
    val fill = highlighter ?: colors.ink
    val textColor = when {
        !selected -> colors.mutedInk
        highlighter != null -> colors.onHighlighter
        else -> colors.onInk
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .toggleable(value = selected, enabled = enabled, role = role, onValueChange = onSelectedChange)
            .then(
                if (selected) {
                    Modifier.background(fill, CircleShape)
                } else {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = colors.perforation,
                            cornerRadius = CornerRadius(size.height / 2),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                            ),
                            topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
                            size = size.copy(width = size.width - 1.5.dp.toPx(), height = size.height - 1.5.dp.toPx()),
                        )
                    }
                },
            )
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = textColor, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}
