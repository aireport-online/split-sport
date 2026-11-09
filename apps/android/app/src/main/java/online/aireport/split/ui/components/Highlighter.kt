package online.aireport.split.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.aireport.split.data.model.GroupColor
import online.aireport.split.ui.theme.SplitTheme

private val Overhang = 6.dp
private const val TILT_DEGREES = -1f

/**
 * A highlighter swiped behind the content: a little taller than the lowercase letters, tilted
 * about -1° and with slightly uneven ends. The unevenness is derived from [seed] (for a name:
 * its hash), so the same name always gets the same stroke.
 */
fun Modifier.highlighter(color: Color, seed: Int): Modifier = drawBehind {
    val top = size.height * 0.08f
    val bottom = size.height * 0.94f
    val middle = (top + bottom) / 2
    val unevenness = 2.5.dp.toPx()
    // Six stable pseudo-random offsets between 0 and `unevenness`.
    val mixed = seed * -0x61c88647
    fun offset(index: Int): Float = ((mixed ushr (index * 5)) and 0x1f) / 31f * unevenness
    val stroke = Path().apply {
        moveTo(offset(0), top + offset(1) / 2)
        lineTo(size.width - offset(2), top)
        lineTo(size.width, middle)
        lineTo(size.width - offset(3), bottom - offset(4) / 2)
        lineTo(offset(5), bottom)
        lineTo(0f, middle + offset(1) / 2)
        close()
    }
    rotate(TILT_DEGREES) { drawPath(stroke, color) }
}

/**
 * A person: never an avatar, but their name with their highlighter swiped behind it. [position]
 * is the member's position in the group, which decides the colour. The stroke sticks out 6 dp
 * left and right of the name.
 */
@Composable
fun PersonName(
    name: String,
    position: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = SplitTheme.colors
    Text(
        text = name,
        modifier = modifier
            .highlighter(colors.memberHighlighter(position), seed = name.hashCode())
            .padding(horizontal = Overhang),
        color = colors.onHighlighter,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A group's name, underlined with a thick stroke of the group's highlighter that runs behind
 * the lower part of the letters.
 */
@Composable
fun GroupName(
    name: String,
    color: GroupColor,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    maxLines: Int = 2,
) {
    val highlighter = SplitTheme.colors.highlighter(color)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = name,
        modifier = modifier.drawBehind {
            val textLayout = layout ?: return@drawBehind
            val em = style.fontSize.toPx()
            for (line in 0 until textLayout.lineCount) {
                val left = textLayout.getLineLeft(line)
                val right = textLayout.getLineRight(line)
                drawRect(
                    color = highlighter,
                    topLeft = Offset(left, textLayout.getLineBaseline(line) - 0.24f * em),
                    size = Size(right - left, 0.34f * em),
                )
            }
        },
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}
