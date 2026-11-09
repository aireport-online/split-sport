package online.aireport.split.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import online.aireport.split.data.model.GroupColor

/** A pale fill with the dark ink of the same hue, for group tiles and member avatars. */
@Immutable
data class Tint(val background: Color, val ink: Color)

/** The eight tints of `docs/design.md`, in the order members are given theirs. */
@Immutable
class TintPalette private constructor(private val tints: Map<GroupColor, Tint>) {

    fun of(color: GroupColor): Tint = tints.getValue(color)

    /** A member's tint is their position in `GroupDetail.members` modulo 8. */
    fun ofMember(position: Int): Tint = of(GroupColor.entries[position.mod(GroupColor.entries.size)])

    companion object {
        val Light = TintPalette(
            mapOf(
                GroupColor.BLUE to Tint(Color(0xFFDCF1FF), Color(0xFF0A3A80)),
                GroupColor.AMBER to Tint(Color(0xFFFFF1CF), Color(0xFF7A3E00)),
                GroupColor.TEAL to Tint(Color(0xFFD5F3F1), Color(0xFF0B4F4A)),
                GroupColor.ROSE to Tint(Color(0xFFFFE1EC), Color(0xFF8A1846)),
                GroupColor.GREEN to Tint(Color(0xFFDCFAE5), Color(0xFF0B4A22)),
                GroupColor.CORAL to Tint(Color(0xFFFFE4DC), Color(0xFF8A2B12)),
                GroupColor.LIME to Tint(Color(0xFFECF6CC), Color(0xFF3F5200)),
                GroupColor.SLATE to Tint(Color(0xFFE9EDF2), Color(0xFF2F3A46)),
            ),
        )

        val Dark = TintPalette(
            mapOf(
                GroupColor.BLUE to Tint(Color(0xFF12305C), Color(0xFFCFE6FF)),
                GroupColor.AMBER to Tint(Color(0xFF4A3206), Color(0xFFFFE2A6)),
                GroupColor.TEAL to Tint(Color(0xFF0E3D3A), Color(0xFFBDEEEA)),
                GroupColor.ROSE to Tint(Color(0xFF52142E), Color(0xFFFFC9DC)),
                GroupColor.GREEN to Tint(Color(0xFF113D20), Color(0xFFC2F2D0)),
                GroupColor.CORAL to Tint(Color(0xFF52200F), Color(0xFFFFCDBF)),
                GroupColor.LIME to Tint(Color(0xFF2F3B08), Color(0xFFE0F0A8)),
                GroupColor.SLATE to Tint(Color(0xFF2A3138), Color(0xFFD7DEE6)),
            ),
        )
    }
}
