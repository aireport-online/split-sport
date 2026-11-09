package online.aireport.split.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import online.aireport.split.R

/** Asks for confirmation before something that cannot be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { QuietButton(confirmLabel, onConfirm, destructive = true) },
        dismissButton = { QuietButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

/** Asks for a name: for a new member or to rename something. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val submit = { if (name.isNotBlank()) onConfirm(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SplitTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = stringResource(R.string.field_name),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = { QuietButton(confirmLabel, submit, enabled = name.isNotBlank()) },
        dismissButton = { QuietButton(stringResource(R.string.action_cancel), onDismiss) },
    )
}

/** Names of people and groups are at most 60 characters on the server. */
const val MAX_NAME_LENGTH = 60
