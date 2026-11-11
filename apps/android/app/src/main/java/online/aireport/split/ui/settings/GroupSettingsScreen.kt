package online.aireport.split.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.SplitTopBar

@Composable
fun GroupSettingsRoute(groupId: String, onBack: () -> Unit, onLeftGroup: () -> Unit) {
    val viewModel = splitViewModel { GroupSettingsViewModel(groupId, it.groupRepository, it.accountRepository) }
    val state by viewModel.uiState.collectAsUiState()
    val context = LocalContext.current
    val shareText = stringResource(R.string.settings_share_text, state.group?.name.orEmpty(), state.inviteLink.orEmpty())

    LaunchedEffect(state.hasLeft) {
        if (state.hasLeft) onLeftGroup()
    }

    GroupSettingsScreen(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onShareInvite = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(send, null))
        },
    )
}

/**
 * Team settings: the team itself (name, sport, club, season, highlighter, penningmeester), the
 * fine list, the team members, the invitation, and leaving.
 */
@Composable
fun GroupSettingsScreen(
    state: GroupSettingsUiState,
    actions: GroupSettingsActions,
    onBack: () -> Unit,
    onShareInvite: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, actions::onMessageShown)

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { SplitTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val group = state.group
        when {
            group != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val enabled = !state.isBusy
                TeamReceipt(group, enabled, actions)
                FineListReceipt(group, enabled, actions)
                MembersReceipt(group, enabled, actions::addMember, actions::renameMember, actions::removeMember)
                InviteReceipt(group, onShareInvite)
                LeaveButton(enabled, actions::leave)
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), actions::retry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}
