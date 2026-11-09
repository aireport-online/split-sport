package online.aireport.split

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import online.aireport.split.core.InviteCode
import online.aireport.split.ui.navigation.SplitApp
import online.aireport.split.ui.theme.SplitTheme

/** The app's only activity; also the target of `split://join/<code>` links. */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer { MainViewModel((application as SplitApplication).container.accountRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a rotation the link that started the activity has already been handled.
        if (savedInstanceState == null) handleInviteLink(intent)
        setContent {
            SplitTheme {
                SplitApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInviteLink(intent)
    }

    private fun handleInviteLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        intent.dataString?.let(InviteCode::fromLink)?.let(viewModel::onInviteLink)
    }
}
