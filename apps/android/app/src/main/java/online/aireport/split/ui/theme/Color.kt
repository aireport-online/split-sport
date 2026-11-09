package online.aireport.split.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import online.aireport.split.data.model.GroupColor

/**
 * The colour roles of the "Kassabon" house style (`docs/design.md`): receipt paper on a desk,
 * printed in ink.
 */
@Immutable
data class SplitColors(
    /** The page ground. */
    val desk: Color,
    /** Receipts, sheets and dialogs. */
    val paper: Color,
    /** Text, primary buttons and outlines. */
    val ink: Color,
    /** Text on a primary button. */
    val onInk: Color,
    /** Labels and secondary lines. */
    val mutedInk: Color,
    /** Dashed dividers and field underlines. */
    val perforation: Color,
    /** The "BETAALD" stamp, destructive actions and errors. */
    val stamp: Color,
) {
    /** Text that lies on a highlighter is the same dark ink in both themes. */
    val onHighlighter: Color get() = OnHighlighter

    /** The highlighter of a group. */
    fun highlighter(color: GroupColor): Color = Highlighters.getValue(color)

    /** A member's highlighter is their position in `GroupDetail.members` modulo 8. */
    fun memberHighlighter(position: Int): Color =
        highlighter(GroupColor.entries[position.mod(GroupColor.entries.size)])
}

internal val LightSplitColors = SplitColors(
    desk = Color(0xFFE8E5DE),
    paper = Color(0xFFFBF8F1),
    ink = Color(0xFF141414),
    onInk = Color(0xFFFBF8F1),
    mutedInk = Color(0xFF615D55),
    perforation = Color(0xFFB5B0A5),
    stamp = Color(0xFFD0342C),
)

internal val DarkSplitColors = SplitColors(
    desk = Color(0xFF171614),
    paper = Color(0xFF24221F),
    ink = Color(0xFFF4F0E7),
    onInk = Color(0xFF171614),
    mutedInk = Color(0xFFA9A499),
    perforation = Color(0xFF4A463F),
    stamp = Color(0xFFFF6B5E),
)

private val OnHighlighter = Color(0xFF141414)

/** The eight highlighters; the same in the dark theme. */
private val Highlighters = mapOf(
    GroupColor.AMBER to Color(0xFFFFF04A),
    GroupColor.ROSE to Color(0xFFFF8CCF),
    GroupColor.GREEN to Color(0xFF8AF0AE),
    GroupColor.CORAL to Color(0xFFFFAE5C),
    GroupColor.BLUE to Color(0xFF8FD4FF),
    GroupColor.LIME to Color(0xFFD2F25A),
    GroupColor.TEAL to Color(0xFF74E6DA),
    GroupColor.SLATE to Color(0xFFD8D4C9),
)
