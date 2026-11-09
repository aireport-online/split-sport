package online.aireport.split.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import online.aireport.split.ui.common.UiText

/**
 * Shows [message] in a snackbar when there is one and reports it as shown, so the view model
 * can clear it.
 */
@Composable
fun MessageEffect(message: UiText?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val text = message?.resolve()
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        if (text != null) {
            // Clear first: the message is then not shown again after a rotation.
            currentOnShown()
            snackbarHostState.showSnackbar(text)
        }
    }
}
