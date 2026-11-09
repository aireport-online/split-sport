package online.aireport.split.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.GroupColorPicker
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.MemberAvatar
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.NameDialog
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.QuietButton
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar
import online.aireport.split.ui.components.memberLabel

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
        onBack = onBack,
        onRetry = viewModel::retry,
        onRename = viewModel::rename,
        onRecolor = viewModel::recolor,
        onAddMember = viewModel::addMember,
        onRenameMember = viewModel::renameMember,
        onRemoveMember = viewModel::removeMember,
        onShareInvite = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
            context.startActivity(Intent.createChooser(send, null))
        },
        onLeave = viewModel::leave,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun GroupSettingsScreen(
    state: GroupSettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRename: (String) -> Unit,
    onRecolor: (GroupColor) -> Unit,
    onAddMember: (String) -> Unit,
    onRenameMember: (memberId: String, name: String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onShareInvite: () -> Unit,
    onLeave: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
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
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val enabled = !state.isBusy
                GroupCard(group, enabled, onRename, onRecolor)
                MembersCard(group, enabled, onAddMember, onRenameMember, onRemoveMember)
                InviteCard(group, onShareInvite)
                LeaveButton(enabled, onLeave)
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}

@Composable
private fun GroupCard(group: GroupDetail, enabled: Boolean, onRename: (String) -> Unit, onRecolor: (GroupColor) -> Unit) {
    var name by rememberSaveable(group.name) { mutableStateOf(group.name) }
    val canSave = enabled && name.isNotBlank() && name.trim() != group.name
    SplitCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.settings_group))
            SplitTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = stringResource(R.string.field_name),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onRename(name) }),
            )
            if (canSave) PrimaryButton(stringResource(R.string.action_save), onClick = { onRename(name) })
            Text(
                text = stringResource(R.string.settings_color),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GroupColorPicker(selected = group.color, onSelect = onRecolor, previewName = group.name, enabled = enabled)
        }
    }
}

@Composable
private fun MembersCard(
    group: GroupDetail,
    enabled: Boolean,
    onAddMember: (String) -> Unit,
    onRenameMember: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
) {
    var addDialogOpen by rememberSaveable { mutableStateOf(false) }
    var renamingMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingMemberId by rememberSaveable { mutableStateOf<String?>(null) }

    SplitCard {
        Column(Modifier.padding(vertical = 16.dp)) {
            SectionTitle(stringResource(R.string.settings_members), Modifier.padding(horizontal = 16.dp))
            group.members.forEachIndexed { position, member ->
                if (position > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MemberRow(
                    member = member,
                    position = position,
                    myMemberId = group.myMemberId,
                    enabled = enabled,
                    onRename = { renamingMemberId = member.id },
                    onRemove = { removingMemberId = member.id },
                )
            }
            QuietButton(
                text = stringResource(R.string.settings_add_member),
                onClick = { addDialogOpen = true },
                modifier = Modifier.padding(horizontal = 8.dp),
                enabled = enabled,
            )
        }
    }

    if (addDialogOpen) {
        NameDialog(
            title = stringResource(R.string.settings_add_member),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name ->
                addDialogOpen = false
                onAddMember(name)
            },
            onDismiss = { addDialogOpen = false },
        )
    }
    group.members.firstOrNull { it.id == renamingMemberId }?.let { member ->
        NameDialog(
            title = stringResource(R.string.settings_rename_member),
            confirmLabel = stringResource(R.string.action_save),
            initialName = member.name,
            onConfirm = { name ->
                renamingMemberId = null
                onRenameMember(member.id, name)
            },
            onDismiss = { renamingMemberId = null },
        )
    }
    group.members.firstOrNull { it.id == removingMemberId }?.let { member ->
        ConfirmDialog(
            title = stringResource(R.string.settings_remove_member_title, member.name),
            text = stringResource(R.string.settings_remove_member_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                removingMemberId = null
                onRemoveMember(member.id)
            },
            onDismiss = { removingMemberId = null },
        )
    }
}

@Composable
private fun MemberRow(
    member: Member,
    position: Int,
    myMemberId: String,
    enabled: Boolean,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member.name, position)
        Column(Modifier.weight(1f)) {
            Text(
                text = memberLabel(member, myMemberId),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.userId == null) {
                Text(
                    text = stringResource(R.string.member_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Only members without an account can be renamed or removed by others.
        if (member.userId == null) {
            Row {
                IconButton(onClick = onRename, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_edit),
                        stringResource(R.string.settings_rename_member_named, member.name),
                    )
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        stringResource(R.string.settings_remove_member_named, member.name),
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteCard(group: GroupDetail, onShareInvite: () -> Unit) {
    SplitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.settings_invite))
            Text(
                text = stringResource(R.string.settings_invite_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = group.inviteCode,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            PrimaryButton(stringResource(R.string.settings_share_invite), onShareInvite)
        }
    }
}

@Composable
private fun LeaveButton(enabled: Boolean, onLeave: () -> Unit) {
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    SecondaryButton(
        text = stringResource(R.string.settings_leave),
        onClick = { confirmOpen = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        destructive = true,
    )
    if (confirmOpen) {
        ConfirmDialog(
            title = stringResource(R.string.settings_leave_title),
            text = stringResource(R.string.settings_leave_text),
            confirmLabel = stringResource(R.string.settings_leave),
            onConfirm = {
                confirmOpen = false
                onLeave()
            },
            onDismiss = { confirmOpen = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}
