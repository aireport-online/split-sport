package online.aireport.split.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import online.aireport.split.R
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.BottomActionBar
import online.aireport.split.ui.components.ErrorText
import online.aireport.split.ui.components.GroupName
import online.aireport.split.ui.components.PersonChip
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

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
    val invite = state.invite
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { SplitTopBar(title = stringResource(R.string.join_title), onBack = onBack) },
        bottomBar = {
            if (invite != null) {
                BottomActionBar {
                    PrimaryButton(
                        text = stringResource(if (invite.alreadyMember) R.string.join_open_group else R.string.join_action),
                        onClick = onJoin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canJoin,
                        loading = state.isJoining,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Receipt(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SplitTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        label = stringResource(R.string.join_code),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isJoining,
                        supportingText = stringResource(R.string.join_code_hint),
                        textStyle = FigureTextStyle.copy(letterSpacing = 0.12.em),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onLookUp() }),
                    )
                    if (invite == null) {
                        SecondaryButton(
                            text = stringResource(R.string.join_look_up),
                            onClick = onLookUp,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canLookUp,
                            loading = state.isLookingUp,
                        )
                    }
                    if (state.error != null) ErrorText(state.error.resolve())
                }
            }

            if (invite != null) InviteReceipt(invite, state, onIdentityChange)
        }
    }
}

/** The group behind the code and, when it has members without an account, "Wie ben jij?". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteReceipt(invite: InvitePreview, state: JoinUiState, onIdentityChange: (Identity) -> Unit) {
    val muted = SplitTheme.colors.mutedInk
    Receipt(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GroupName(invite.name, invite.color)
            Text(
                text = pluralStringResource(R.plurals.member_count, invite.memberCount, invite.memberCount),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            if (invite.alreadyMember) {
                Text(
                    text = stringResource(R.string.join_already_member),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.asksIdentity) {
                Text(
                    text = stringResource(R.string.join_who_are_you),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(R.string.join_who_are_you_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    invite.unclaimedMembers.forEach { member ->
                        val identity = Identity.ExistingMember(member.id)
                        PersonChip(
                            name = member.name,
                            selected = state.identity == identity,
                            onSelectedChange = { onIdentityChange(identity) },
                            enabled = !state.isJoining,
                            role = Role.RadioButton,
                        )
                    }
                    PersonChip(
                        name = stringResource(R.string.join_not_listed),
                        selected = state.identity == Identity.NewMember,
                        onSelectedChange = { onIdentityChange(Identity.NewMember) },
                        enabled = !state.isJoining,
                        role = Role.RadioButton,
                    )
                }
            }
        }
    }
}
