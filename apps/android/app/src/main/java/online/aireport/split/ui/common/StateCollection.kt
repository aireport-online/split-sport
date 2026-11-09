package online.aireport.split.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects screen state on the immediate main dispatcher. Text fields write their value to the
 * view model and read it back from this state; with the default dispatcher that round trip takes
 * a frame, and fast typing then loses characters or moves the cursor.
 */
@Composable
fun <T> StateFlow<T>.collectAsUiState(): State<T> =
    collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
