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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
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
    val unevenness = 2.5.dp.toPx()
    // Four stable pseudo-random offsets between 0 and `unevenness`, one per corner.
    val mixed = seed * -0x61c88647
    fun offset(corner: Int): Float = ((mixed ushr (corner * 7)) and 0x7f) / 127f * unevenness
    val stroke = Path().apply {
        moveTo(offset(0), top)
        lineTo(size.width - offset(1), top)
        lineTo(size.width - offset(2), bottom)
        lineTo(offset(3), bottom)
        close()
    }
    rotate(TILT_DEGREES) { drawPath(stroke, color) }
}

/**
 * A person: never an avatar, but their name with their highlighter swiped behind it. [position]
 * is the member's position in the team, which decides the colour. The stroke sticks out 6 dp
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
    StrokedName(name, colors.memberHighlighter(position), colors.onHighlighter, modifier, style)
}

/**
 * The team pot. It is not a person, so it has no highlighter: its name is printed as a label,
 * on-ink text on an ink stroke of the same shape.
 */
@Composable
fun PotName(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = SplitTheme.colors
    StrokedName(name, colors.ink, colors.onInk, modifier, style)
}

/** A member of the team by name: a person with their highlighter, or the pot as its ink label. */
@Composable
fun MemberName(
    member: Member,
    position: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    if (member.isPot) PotName(member.name, modifier, style) else PersonName(member.name, position, modifier, style)
}

/** The member with [memberId] in [group] by name; "Onbekend" when there is no such member. */
@Composable
fun MemberName(
    group: GroupDetail,
    memberId: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val member = memberId?.let(group::member)
    if (member != null) {
        MemberName(member, group.memberPosition(member.id), modifier, style)
    } else {
        Text(stringResource(R.string.member_unknown), modifier, style = style, maxLines = 1)
    }
}

@Composable
private fun StrokedName(name: String, stroke: Color, textColor: Color, modifier: Modifier, style: TextStyle) {
    Text(
        text = name,
        modifier = modifier
            .highlighter(stroke, seed = name.hashCode())
            .padding(horizontal = Overhang),
        color = textColor,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A team's name, underlined with a thick stroke of the group's highlighter. On light paper
 * the stroke runs behind the lower part of the letters; on dark paper light letters would
 * be unreadable on it, so there it sits just below them.
 */
@Composable
fun GroupName(
    name: String,
    color: GroupColor,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    maxLines: Int = 2,
) {
    val colors = SplitTheme.colors
    val highlighter = colors.highlighter(color)
    val darkPaper = colors.paper.luminance() < 0.5f
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = name,
        modifier = modifier.drawWithContent {
            drawContent()
            val textLayout = layout ?: return@drawWithContent
            val em = style.fontSize.toPx()
            for (line in 0 until textLayout.lineCount) {
                val baseline = textLayout.getLineBaseline(line)
                val left = textLayout.getLineLeft(line)
                val right = textLayout.getLineRight(line)
                if (darkPaper) {
                    val top = baseline + 0.06f * em
                    drawRect(highlighter, topLeft = Offset(left, top), size = Size(right - left, 0.16f * em))
                } else {
                    val top = baseline - 0.24f * em
                    clipRect(left, top, right, top + 0.34f * em) {
                        drawRect(highlighter)
                        drawText(textLayout, color = colors.onHighlighter)
                    }
                }
            }
        },
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
    )
}
