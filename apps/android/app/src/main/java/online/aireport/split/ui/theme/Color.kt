package online.aireport.split.ui.theme

import androidx.compose.ui.graphics.Color

/** The colour tokens of `docs/design.md`, light and dark. */
internal object Brand {
    val groundLight = Color(0xFFF3F5F7)
    val groundDark = Color(0xFF101214)
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceDark = Color(0xFF1A1D20)
    val borderLight = Color(0xFFE6E6E6)
    val borderDark = Color(0xFF2C3034)
    val textLight = Color(0xFF1B1D1F)
    val textDark = Color(0xFFECEEF0)
    val mutedLight = Color(0xFF5C6064)
    val mutedDark = Color(0xFFA0A5AB)
    val accentLight = Color(0xFF1D5FD6)
    val accentDark = Color(0xFF7FB0FF)
    val onAccentLight = Color(0xFFFFFFFF)
    val onAccentDark = Color(0xFF06224F)
    val positiveLight = Color(0xFF2F7A45)
    val positiveDark = Color(0xFF6FCF8B)
    val negativeLight = Color(0xFFC41C29)
    val negativeDark = Color(0xFFFF8A8F)

    // Not in the design tokens: the outline of text fields and other controls needs more
    // contrast against a card than the hairline border has (WCAG 1.4.11 asks for 3:1).
    val controlOutlineLight = Color(0xFF8A9096)
    val controlOutlineDark = Color(0xFF6B7178)
}
