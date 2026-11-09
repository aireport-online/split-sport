package online.aireport.split.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The same size in sp that ignores the user's font scale, for text inside fixed-size badges. */
@Composable
@ReadOnlyComposable
fun TextUnit.nonScaled(): TextUnit = (value / LocalDensity.current.fontScale).sp
