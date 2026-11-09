package online.aireport.split.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.aireport.split.MainViewModel

/** The root of the UI: waits for the stored session, then shows the navigation graph. */
@Composable
fun SplitApp(viewModel: MainViewModel) {
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()
    val pendingInviteCode by viewModel.pendingInviteCode.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Reading the session takes a few milliseconds; the page colour shows until then.
        isSignedIn?.let { signedIn ->
            SplitNavHost(
                isSignedIn = signedIn,
                pendingInviteCode = pendingInviteCode,
                onInviteHandled = viewModel::onInviteHandled,
            )
        }
    }
}
