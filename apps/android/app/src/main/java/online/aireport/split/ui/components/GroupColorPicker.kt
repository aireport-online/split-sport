package online.aireport.split.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor

/** The eight group tints as tiles; the chosen one has a ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupColorPicker(
    selected: GroupColor,
    onSelect: (GroupColor) -> Unit,
    previewName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GroupColor.entries.forEach { color ->
            val isSelected = color == selected
            val description = stringResource(color.labelRes)
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(shape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(color) },
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(6.dp)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                GroupTile(name = previewName.ifBlank { description }, color = color, size = 40.dp)
            }
        }
    }
}

private val GroupColor.labelRes: Int
    get() = when (this) {
        GroupColor.BLUE -> R.string.color_blue
        GroupColor.AMBER -> R.string.color_amber
        GroupColor.TEAL -> R.string.color_teal
        GroupColor.ROSE -> R.string.color_rose
        GroupColor.GREEN -> R.string.color_green
        GroupColor.CORAL -> R.string.color_coral
        GroupColor.LIME -> R.string.color_lime
        GroupColor.SLATE -> R.string.color_slate
    }
