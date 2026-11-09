package online.aireport.split.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import online.aireport.split.R

/** Everything that is "printed big": the wordmark, titles, buttons and tabs. */
val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))

/** Everything a till would print: amounts, receipt lines, labels, values and running text. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun display(size: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.01.em,
)

private fun mono(size: TextUnit, lineHeight: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
    // A till does not join "->" into an arrow.
    fontFeatureSettings = "\"liga\" 0, \"calt\" 0",
)

/**
 * The two typefaces on the Material 3 type scale, so stock components (dialogs, menus, the date
 * picker) print like the rest: display, headline and title styles and `labelLarge` (buttons,
 * tabs) are Barlow Condensed Bold, body and the small labels are JetBrains Mono.
 */
internal val SplitTypography = Typography(
    displayLarge = display(84.sp, 84.sp),
    displayMedium = display(52.sp, 52.sp),
    displaySmall = display(40.sp, 44.sp),
    // Screen titles.
    headlineLarge = display(30.sp, 34.sp),
    headlineMedium = display(28.sp, 32.sp),
    headlineSmall = display(24.sp, 28.sp),
    // Receipt titles.
    titleLarge = display(22.sp, 26.sp),
    titleMedium = display(18.sp, 22.sp),
    titleSmall = display(16.sp, 20.sp),
    // Buttons and tabs.
    labelLarge = display(17.sp, 20.sp),
    labelMedium = mono(13.sp, 18.sp, FontWeight.Medium),
    labelSmall = mono(12.sp, 16.sp),
    // Receipt lines, field values and running text.
    bodyLarge = mono(15.sp, 22.sp),
    // "Small": labels and secondary lines.
    bodyMedium = mono(13.sp, 18.sp),
    bodySmall = mono(12.sp, 16.sp),
)

/** The headline figure on a receipt: "+ € 109,55". */
val FigureTextStyle: TextStyle = mono(30.sp, 36.sp, FontWeight.Medium).copy(letterSpacing = (-0.02).em)
