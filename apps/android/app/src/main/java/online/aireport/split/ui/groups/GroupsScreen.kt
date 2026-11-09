package online.aireport.split.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.EmptyState
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.GroupTile
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.SplitTopBar
import online.aireport.split.ui.components.balanceColor
import online.aireport.split.ui.components.ownBalanceSentence
import online.aireport.split.ui.components.signedBalance

@Composable
fun GroupsRoute(onOpenGroup: (String) -> Unit, onJoinWithCode: () -> Unit, onOpenProfile: () -> Unit) {
    val viewModel = splitViewModel { GroupsViewModel(it.groupRepository, it.groupChanges) }
    val state by viewModel.uiState.collectAsUiState()

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let { groupId ->
            viewModel.onCreatedGroupOpened()
            onOpenGroup(groupId)
        }
    }

    GroupsScreen(
        state = state,
        onOpenGroup = onOpenGroup,
        onCreateGroup = viewModel::createGroup,
        onJoinWithCode = onJoinWithCode,
        onOpenProfile = onOpenProfile,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: (String, GroupColor) -> Unit,
    onJoinWithCode: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var newGroupDialogOpen by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        topBar = {
            SplitTopBar(
                title = stringResource(R.string.groups_title),
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.profile_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                GroupList(
                    state = state,
                    onOpenGroup = onOpenGroup,
                    onNewGroup = { newGroupDialogOpen = true },
                    onJoinWithCode = onJoinWithCode,
                )
            }
        }
    }

    if (newGroupDialogOpen) {
        NewGroupDialog(
            onCreate = { name, color ->
                newGroupDialogOpen = false
                onCreateGroup(name, color)
            },
            onDismiss = { newGroupDialogOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupList(
    state: GroupsUiState,
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
    onJoinWithCode: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balance") { TotalBalanceCard(state.totalBalanceCents) }
        item(key = "actions") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.groups_new),
                    onClick = onNewGroup,
                    loading = state.isCreating,
                )
                SecondaryButton(stringResource(R.string.groups_join), onJoinWithCode)
            }
        }
        if (state.groups.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.groups_empty)) }
        } else {
            item(key = "groups") {
                SplitCard {
                    state.groups.forEachIndexed { index, group ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        GroupRow(group, onClick = { onOpenGroup(group.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalBalanceCard(totalBalanceCents: Long) {
    SplitCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.groups_total_balance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = ownBalanceSentence(totalBalanceCents),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = balanceColor(totalBalanceCents),
            )
        }
    }
}

@Composable
private fun GroupRow(group: GroupSummary, onClick: () -> Unit) {
    val balanceDescription = ownBalanceSentence(group.myBalanceCents)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupTile(group.name, group.color)
        Column(Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.member_count, group.memberCount, group.memberCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = signedBalance(group.myBalanceCents),
            modifier = Modifier.semantics { contentDescription = balanceDescription },
            style = MaterialTheme.typography.titleMedium,
            color = balanceColor(group.myBalanceCents),
        )
    }
}
