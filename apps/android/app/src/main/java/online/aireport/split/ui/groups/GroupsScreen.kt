package online.aireport.split.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.formatSignedCents
import online.aireport.split.data.NewTeam
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.common.teamLine
import online.aireport.split.ui.components.BottomActionBar
import online.aireport.split.ui.components.EmptyState
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.GroupName
import online.aireport.split.ui.components.HomeTopBar
import online.aireport.split.ui.components.IconActionButton
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptLine
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.balanceColor
import online.aireport.split.ui.components.ownBalanceSentence
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

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
        onCreateTeam = viewModel::createTeam,
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
    onCreateTeam: (NewTeam) -> Unit,
    onJoinWithCode: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var newTeamSheetOpen by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        topBar = {
            HomeTopBar(
                title = stringResource(R.string.groups_title),
                actions = {
                    IconActionButton(
                        icon = painterResource(R.drawable.ic_person),
                        contentDescription = stringResource(R.string.profile_title),
                        onClick = onOpenProfile,
                    )
                },
            )
        },
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    text = stringResource(R.string.groups_new),
                    onClick = { newTeamSheetOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    loading = state.isCreating,
                )
            }
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
                GroupList(state, onOpenGroup, onJoinWithCode)
            }
        }
    }

    if (newTeamSheetOpen) {
        NewTeamSheet(
            onCreate = { team ->
                newTeamSheetOpen = false
                onCreateTeam(team)
            },
            onDismiss = { newTeamSheetOpen = false },
        )
    }
}

@Composable
private fun GroupList(state: GroupsUiState, onOpenGroup: (String) -> Unit, onJoinWithCode: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balance") { TotalBalanceReceipt(state.totalBalanceCents) }
        if (state.groups.isEmpty()) {
            item(key = "empty") { EmptyState(stringResource(R.string.groups_empty)) }
        } else {
            items(state.groups, key = { it.id }) { group ->
                GroupReceipt(group, onClick = { onOpenGroup(group.id) })
            }
        }
        item(key = "join") {
            SecondaryButton(stringResource(R.string.groups_join), onJoinWithCode, Modifier.fillMaxWidth())
        }
    }
}

/** The "Jouw saldo" receipt: the balance over all teams as a large figure, and in words. */
@Composable
private fun TotalBalanceReceipt(totalBalanceCents: Long) {
    val sentence = ownBalanceSentence(totalBalanceCents)
    Receipt(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(ReceiptPadding)
                // Read as one thing; the sentence says what the figure shows.
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.groups_total_balance), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = formatSignedCents(totalBalanceCents),
                modifier = Modifier.clearAndSetSemantics {},
                style = FigureTextStyle,
                color = balanceColor(totalBalanceCents),
            )
            Text(sentence, style = MaterialTheme.typography.bodyMedium, color = SplitTheme.colors.mutedInk)
        }
    }
}

/** One small receipt per team: the name, sport, club and season, the number of team members and your own balance. */
@Composable
private fun GroupReceipt(group: GroupSummary, onClick: () -> Unit) {
    val balanceDescription = ownBalanceSentence(group.myBalanceCents)
    Receipt(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(start = ReceiptPadding, end = ReceiptPadding, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GroupName(group.name, group.color)
            Text(
                text = teamLine(group.sport, group.club, group.season),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
            )
            ReceiptLine(
                name = {
                    Text(
                        text = pluralStringResource(R.plurals.member_count, group.memberCount, group.memberCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SplitTheme.colors.mutedInk,
                        maxLines = 1,
                    )
                },
                amount = {
                    Text(
                        text = formatSignedCents(group.myBalanceCents),
                        modifier = Modifier.semantics { contentDescription = balanceDescription },
                        style = MaterialTheme.typography.bodyLarge,
                        color = balanceColor(group.myBalanceCents),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
