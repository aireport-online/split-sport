package online.aireport.split.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.GroupTile
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar

@Composable
fun JoinRoute(initialCode: String?, onBack: () -> Unit, onOpenGroup: (String) -> Unit) {
    val viewModel = splitViewModel { JoinViewModel(initialCode, it.groupRepository) }
    val state by viewModel.uiState.collectAsUiState()

    LaunchedEffect(state.openGroupId) {
        state.openGroupId?.let { groupId ->
            viewModel.onGroupOpened()
            onOpenGroup(groupId)
        }
    }

    JoinScreen(
        state = state,
        onBack = onBack,
        onCodeChange = viewModel::onCodeChange,
        onLookUp = viewModel::lookUp,
        onIdentityChange = viewModel::onIdentityChange,
        onJoin = viewModel::join,
    )
}

@Composable
fun JoinScreen(
    state: JoinUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onLookUp: () -> Unit,
    onIdentityChange: (Identity) -> Unit,
    onJoin: () -> Unit,
) {
    Scaffold(
        topBar = { SplitTopBar(title = stringResource(R.string.join_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SplitCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SplitTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        label = stringResource(R.string.join_code),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isJoining,
                        supportingText = stringResource(R.string.join_code_hint),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onLookUp() }),
                    )
                    if (state.invite == null) {
                        SecondaryButton(
                            text = stringResource(R.string.join_look_up),
                            onClick = onLookUp,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canLookUp,
                            loading = state.isLookingUp,
                        )
                    }
                }
            }

            if (state.invite != null) {
                InviteCard(state.invite, state, onIdentityChange)
            }

            if (state.error != null) {
                Text(
                    text = state.error.resolve(),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.invite != null) {
                PrimaryButton(
                    text = stringResource(if (state.invite.alreadyMember) R.string.join_open_group else R.string.join_action),
                    onClick = onJoin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canJoin,
                    loading = state.isJoining,
                )
            }
        }
    }
}

@Composable
private fun InviteCard(invite: InvitePreview, state: JoinUiState, onIdentityChange: (Identity) -> Unit) {
    SplitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupTile(invite.name, invite.color)
                Column {
                    Text(invite.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = pluralStringResource(R.plurals.member_count, invite.memberCount, invite.memberCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (invite.alreadyMember) {
                Text(
                    text = stringResource(R.string.join_already_member),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.asksIdentity) {
                Text(
                    text = stringResource(R.string.join_who_are_you),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(Modifier.selectableGroup()) {
                    invite.unclaimedMembers.forEach { member ->
                        val identity = Identity.ExistingMember(member.id)
                        IdentityOption(member.name, state.identity == identity, !state.isJoining) {
                            onIdentityChange(identity)
                        }
                    }
                    IdentityOption(
                        label = stringResource(R.string.join_not_listed),
                        selected = state.identity == Identity.NewMember,
                        enabled = !state.isJoining,
                        onSelect = { onIdentityChange(Identity.NewMember) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityOption(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row is the option; the radio button only shows the state.
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
