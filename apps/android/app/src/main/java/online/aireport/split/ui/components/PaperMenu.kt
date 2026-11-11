package online.aireport.split.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.theme.SplitTheme

/** A dropdown menu as a piece of paper: no shadow, an ink outline. */
@Composable
fun PaperMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = SplitTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.small,
        containerColor = colors.paper,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.5.dp, colors.ink),
        content = content,
    )
}

/**
 * Picks one of a short list of options: looks like a field (label, value, underline) and opens a
 * menu when tapped.
 */
@Composable
fun <T> OptionPicker(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SplitTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .heightIn(min = 48.dp)
                .fieldUnderline(colors.perforation, solid = colors.ink.takeIf { expanded })
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.mutedInk)
                Text(optionLabel(selected), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, Modifier.size(20.dp))
        }
        PaperMenu(expanded = expanded, onDismiss = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
