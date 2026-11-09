package online.aireport.split.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.aireport.split.ui.theme.SplitTheme

private val OutlineWidth = 1.5.dp
private val LabelPadding = 6.dp

/**
 * Tabs and segmented controls: a 1.5 dp ink outline around equally wide options; the selected
 * one is filled with ink. When the labels do not fit next to each other (a large font on a
 * narrow screen) the options are stacked in rows of two.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SplitTheme.colors
    val shape = MaterialTheme.shapes.small
    val style = MaterialTheme.typography.labelLarge
    val labels = options.map { label(it) }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .border(OutlineWidth, colors.ink, shape)
            .clip(shape)
            .selectableGroup(),
    ) {
        val density = LocalDensity.current
        val optionsPerRow = remember(labels, style, constraints.maxWidth, density) {
            val outline = with(density) { OutlineWidth.roundToPx() }
            val padding = with(density) { LabelPadding.roundToPx() }
            val widthPerOption = (constraints.maxWidth - (options.size - 1) * outline) / options.size
            val widestLabel = labels.maxOf { textMeasurer.measure(it, style, maxLines = 1).size.width }
            if (widestLabel + 2 * padding <= widthPerOption) options.size else 2
        }
        Column {
            options.indices.chunked(optionsPerRow).forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(OutlineWidth)
                            .background(colors.ink),
                    )
                }
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { column, index ->
                        val isSelected = options[index] == selected
                        if (column > 0) {
                            Box(
                                Modifier
                                    .width(OutlineWidth)
                                    .fillMaxHeight()
                                    .background(colors.ink),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (isSelected) colors.ink else Color.Transparent)
                                .selectable(
                                    selected = isSelected,
                                    enabled = enabled,
                                    role = Role.Tab,
                                    onClick = { onSelect(options[index]) },
                                )
                                .heightIn(min = 48.dp)
                                .padding(horizontal = LabelPadding, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = labels[index],
                                color = if (isSelected) colors.onInk else colors.ink,
                                style = style,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
