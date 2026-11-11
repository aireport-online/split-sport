package online.aireport.split.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.HighlighterPicker
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.LinkButton
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.NameDialog
import online.aireport.split.ui.components.MemberLabel
import online.aireport.split.ui.components.Pill
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

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
                GroupReceipt(group, enabled, onRename, onRecolor)
                MembersReceipt(group, enabled, onAddMember, onRenameMember, onRemoveMember)
                InviteReceipt(group, onShareInvite)
                LeaveButton(enabled, onLeave)
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }
}

@Composable
private fun GroupReceipt(group: GroupDetail, enabled: Boolean, onRename: (String) -> Unit, onRecolor: (GroupColor) -> Unit) {
    var name by rememberSaveable(group.name) { mutableStateOf(group.name) }
    val canSave = enabled && name.isNotBlank() && name.trim() != group.name
    Receipt(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            if (canSave) SecondaryButton(stringResource(R.string.action_save), onClick = { onRename(name) }, small = true)
            Column {
                Text(
                    text = stringResource(R.string.settings_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitTheme.colors.mutedInk,
                )
                HighlighterPicker(selected = group.color, onSelect = onRecolor, enabled = enabled)
            }
        }
    }
}

@Composable
private fun MembersReceipt(
    group: GroupDetail,
    enabled: Boolean,
    onAddMember: (String) -> Unit,
    onRenameMember: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
) {
    var addDialogOpen by rememberSaveable { mutableStateOf(false) }
    var renamingMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var removingMemberId by rememberSaveable { mutableStateOf<String?>(null) }

    Receipt(Modifier.fillMaxWidth()) {
        SectionTitle(
            text = stringResource(R.string.settings_members),
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = ReceiptPadding, bottom = 6.dp),
        )
        group.members.forEachIndexed { position, member ->
            MemberRow(
                member = member,
                position = position,
                myMemberId = group.myMemberId,
                enabled = enabled,
                onRename = { renamingMemberId = member.id },
                onRemove = { removingMemberId = member.id },
            )
        }
        SecondaryButton(
            text = stringResource(R.string.settings_add_member),
            onClick = { addDialogOpen = true },
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 6.dp, bottom = 12.dp),
            enabled = enabled,
            small = true,
        )
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
            .heightIn(min = 48.dp)
            .padding(start = ReceiptPadding, end = ReceiptPadding - 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberLabel(member, position, myMemberId, Modifier.weight(1f, fill = false))
            if (member.userId == null) Pill(stringResource(R.string.member_no_account))
        }
        // Only members without an account can be renamed or removed by others.
        if (member.userId == null) {
            IconActionButton(
                icon = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.settings_rename_member_named, member.name),
                onClick = onRename,
                enabled = enabled,
            )
            IconActionButton(
                icon = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.settings_remove_member_named, member.name),
                onClick = onRemove,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun InviteReceipt(group: GroupDetail, onShareInvite: () -> Unit) {
    Receipt(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.settings_invite))
            Text(
                text = stringResource(R.string.settings_invite_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
            )
            Text(text = group.inviteCode, style = FigureTextStyle.copy(letterSpacing = 0.12.em))
            PrimaryButton(stringResource(R.string.settings_share_invite), onShareInvite, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LeaveButton(enabled: Boolean, onLeave: () -> Unit) {
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    LinkButton(
        text = stringResource(R.string.settings_leave),
        onClick = { confirmOpen = true },
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
        style = MaterialTheme.typography.titleLarge,
    )
}
