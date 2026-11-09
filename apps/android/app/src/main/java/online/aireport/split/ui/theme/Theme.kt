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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LocalSplitColors = staticCompositionLocalOf { LightSplitColors }

/**
 * The house style on Material 3 colour roles, for the stock components that are still used
 * (dialogs, menus, sheets, the date picker, snackbars). The page is `background`/`surface`,
 * everything laid on it is paper (all `surfaceContainer` roles, so there are no tonal
 * surfaces), ink is `primary` and `outline`, the perforation is `outlineVariant`, stamp red is
 * `error`.
 */
private fun SplitColors.toColorScheme(dark: Boolean): ColorScheme {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = ink,
        onPrimaryContainer = onInk,
        inversePrimary = onInk,
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = ink,
        onSecondaryContainer = onInk,
        tertiary = ink,
        onTertiary = onInk,
        tertiaryContainer = ink,
        onTertiaryContainer = onInk,
        background = desk,
        onBackground = ink,
        surface = desk,
        onSurface = ink,
        surfaceVariant = paper,
        onSurfaceVariant = mutedInk,
        surfaceTint = paper,
        inverseSurface = ink,
        inverseOnSurface = onInk,
        error = stamp,
        onError = onInk,
        errorContainer = paper,
        onErrorContainer = stamp,
        outline = ink,
        outlineVariant = perforation,
        scrim = Color.Black,
        surfaceBright = desk,
        surfaceDim = desk,
        surfaceContainerLowest = paper,
        surfaceContainerLow = paper,
        surfaceContainer = paper,
        surfaceContainerHigh = paper,
        surfaceContainerHighest = paper,
    )
}

/** Controls have a 4 dp radius; paper (sheets, dialogs) has barely rounded 2 dp corners. */
private val SplitShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
)

/** The "Kassabon" theme; follows the system's light or dark setting, never dynamic colour. */
@Composable
fun SplitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkSplitColors else LightSplitColors
    CompositionLocalProvider(LocalSplitColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = SplitTypography,
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
