package online.aireport.split.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.aireport.split.data.model.GroupColor

/** Brand colours that have no Material 3 role of their own. */
@Immutable
data class SplitColors(
    /** Money someone gets back. */
    val positive: Color,
    val tints: TintPalette,
)

private val LightSplitColors = SplitColors(positive = Brand.positiveLight, tints = TintPalette.Light)
private val DarkSplitColors = SplitColors(positive = Brand.positiveDark, tints = TintPalette.Dark)

private val LocalSplitColors = staticCompositionLocalOf { LightSplitColors }

/**
 * The design tokens on Material 3 roles. The page is `background`/`surface`, cards are the
 * `surfaceContainer` roles up to `High`, muted text is `onSurfaceVariant`, the hairline border
 * is `outlineVariant`. Containers reuse the blue, green, coral and slate tints.
 */
private val LightColors: ColorScheme = with(TintPalette.Light) {
    lightColorScheme(
        primary = Brand.accentLight,
        onPrimary = Brand.onAccentLight,
        primaryContainer = of(GroupColor.BLUE).background,
        onPrimaryContainer = of(GroupColor.BLUE).ink,
        inversePrimary = Brand.accentDark,
        secondary = Brand.accentLight,
        onSecondary = Brand.onAccentLight,
        secondaryContainer = of(GroupColor.BLUE).background,
        onSecondaryContainer = of(GroupColor.BLUE).ink,
        tertiary = Brand.positiveLight,
        onTertiary = Color.White,
        tertiaryContainer = of(GroupColor.GREEN).background,
        onTertiaryContainer = of(GroupColor.GREEN).ink,
        background = Brand.groundLight,
        onBackground = Brand.textLight,
        surface = Brand.groundLight,
        onSurface = Brand.textLight,
        surfaceVariant = of(GroupColor.SLATE).background,
        onSurfaceVariant = Brand.mutedLight,
        surfaceTint = Brand.surfaceLight,
        inverseSurface = Brand.textLight,
        inverseOnSurface = Brand.textDark,
        error = Brand.negativeLight,
        onError = Color.White,
        errorContainer = of(GroupColor.CORAL).background,
        onErrorContainer = of(GroupColor.CORAL).ink,
        outline = Brand.controlOutlineLight,
        outlineVariant = Brand.borderLight,
        scrim = Color.Black,
        surfaceBright = Brand.groundLight,
        surfaceDim = Brand.groundLight,
        surfaceContainerLowest = Brand.surfaceLight,
        surfaceContainerLow = Brand.surfaceLight,
        surfaceContainer = Brand.surfaceLight,
        surfaceContainerHigh = Brand.surfaceLight,
        surfaceContainerHighest = of(GroupColor.SLATE).background,
    )
}

private val DarkColors: ColorScheme = with(TintPalette.Dark) {
    darkColorScheme(
        primary = Brand.accentDark,
        onPrimary = Brand.onAccentDark,
        primaryContainer = of(GroupColor.BLUE).background,
        onPrimaryContainer = of(GroupColor.BLUE).ink,
        inversePrimary = Brand.accentLight,
        secondary = Brand.accentDark,
        onSecondary = Brand.onAccentDark,
        secondaryContainer = of(GroupColor.BLUE).background,
        onSecondaryContainer = of(GroupColor.BLUE).ink,
        tertiary = Brand.positiveDark,
        onTertiary = of(GroupColor.GREEN).background,
        tertiaryContainer = of(GroupColor.GREEN).background,
        onTertiaryContainer = of(GroupColor.GREEN).ink,
        background = Brand.groundDark,
        onBackground = Brand.textDark,
        surface = Brand.groundDark,
        onSurface = Brand.textDark,
        surfaceVariant = of(GroupColor.SLATE).background,
        onSurfaceVariant = Brand.mutedDark,
        surfaceTint = Brand.surfaceDark,
        inverseSurface = Brand.textDark,
        inverseOnSurface = Brand.textLight,
        error = Brand.negativeDark,
        onError = of(GroupColor.CORAL).background,
        errorContainer = of(GroupColor.CORAL).background,
        onErrorContainer = of(GroupColor.CORAL).ink,
        outline = Brand.controlOutlineDark,
        outlineVariant = Brand.borderDark,
        scrim = Color.Black,
        surfaceBright = Brand.groundDark,
        surfaceDim = Brand.groundDark,
        surfaceContainerLowest = Brand.surfaceDark,
        surfaceContainerLow = Brand.surfaceDark,
        surfaceContainer = Brand.surfaceDark,
        surfaceContainerHigh = Brand.surfaceDark,
        surfaceContainerHighest = of(GroupColor.SLATE).background,
    )
}

/** Cards have a 20 dp radius, controls 10 dp. */
private val SplitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** The Split brand theme; follows the system's light or dark setting, never dynamic colour. */
@Composable
fun SplitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSplitColors provides if (darkTheme) DarkSplitColors else LightSplitColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = SplitShapes,
            content = content,
        )
    }
}

object SplitTheme {
    val colors: SplitColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSplitColors.current
}
