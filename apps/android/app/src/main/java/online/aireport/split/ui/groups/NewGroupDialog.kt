package online.aireport.split.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.ui.components.GroupColorPicker
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.QuietButton
import online.aireport.split.ui.components.SplitTextField

/** Asks for the name and the colour of a new group. */
@Composable
fun NewGroupDialog(onCreate: (String, GroupColor) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(GroupColor.BLUE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SplitTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = stringResource(R.string.field_name),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.groups_new_placeholder),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                GroupColorPicker(selected = color, onSelect = { color = it }, previewName = name)
            }
        },
        confirmButton = {
            QuietButton(
                text = stringResource(R.string.action_create),
                onClick = { onCreate(name, color) },
                enabled = name.isNotBlank(),
            )
        },
        dismissButton = { QuietButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}
