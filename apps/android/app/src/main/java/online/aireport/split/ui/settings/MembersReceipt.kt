package online.aireport.split.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import online.aireport.split.R
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.LinkButton
import online.aireport.split.ui.components.MemberLabel
import online.aireport.split.ui.components.NameDialog
import online.aireport.split.ui.components.Pill
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

/** The people in the team. The pot is a member too, but not one to rename or remove, so it is not listed. */
@Composable
internal fun MembersReceipt(
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
        group.players.forEach { member ->
            MemberRow(
                member = member,
                position = group.memberPosition(member.id),
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
internal fun InviteReceipt(group: GroupDetail, onShareInvite: () -> Unit) {
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
internal fun LeaveButton(enabled: Boolean, onLeave: () -> Unit) {
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

/** The title of a receipt on the settings screen. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
