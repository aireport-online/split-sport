package online.aireport.split.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.ui.theme.SplitTheme

/** The eight highlighters as strokes to pick a group's colour from; the chosen one is outlined. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlighterPicker(
    selected: GroupColor,
    onSelect: (GroupColor) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SplitTheme.colors
    FlowRow(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        // Two tidy rows of four rather than seven and a stray one.
        maxItemsInEachRow = GroupColor.entries.size / 2,
    ) {
        GroupColor.entries.forEach { color ->
            val isSelected = color == selected
            val description = stringResource(color.labelRes)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(color) },
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 22.dp)
                        .highlighter(colors.highlighter(color), seed = color.ordinal + 1),
                    contentAlignment = Alignment.Center,
                ) {
                    InkCheckbox(checked = true, Modifier.alpha(if (isSelected) 1f else 0f).size(16.dp))
                }
            }
        }
    }
}

private val GroupColor.labelRes: Int
    get() = when (this) {
        GroupColor.AMBER -> R.string.color_amber
        GroupColor.ROSE -> R.string.color_rose
        GroupColor.GREEN -> R.string.color_green
        GroupColor.CORAL -> R.string.color_coral
        GroupColor.BLUE -> R.string.color_blue
        GroupColor.LIME -> R.string.color_lime
        GroupColor.TEAL -> R.string.color_teal
        GroupColor.SLATE -> R.string.color_slate
    }
