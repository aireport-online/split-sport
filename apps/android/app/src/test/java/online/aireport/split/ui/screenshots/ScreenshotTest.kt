package online.aireport.split.ui.screenshots

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.data.model.UnclaimedMember
import online.aireport.split.ui.auth.AuthScreen
import online.aireport.split.ui.auth.AuthUiState
import online.aireport.split.ui.expense.ExpenseDraft
import online.aireport.split.ui.expense.ExpenseScreen
import online.aireport.split.ui.expense.ExpenseUiState
import online.aireport.split.ui.expense.ReceiptActions
import online.aireport.split.ui.group.GroupScreen
import online.aireport.split.ui.group.GroupTab
import online.aireport.split.ui.group.GroupUiState
import online.aireport.split.ui.groups.GroupsScreen
import online.aireport.split.ui.groups.GroupsUiState
import online.aireport.split.ui.join.Identity
import online.aireport.split.ui.join.JoinScreen
import online.aireport.split.ui.join.JoinUiState
import online.aireport.split.ui.profile.ProfileScreen
import online.aireport.split.ui.profile.ProfileUiState
import online.aireport.split.ui.settings.GroupSettingsScreen
import online.aireport.split.ui.settings.GroupSettingsUiState
import online.aireport.split.ui.theme.SplitTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens with fake state on the JVM. As a unit test it checks that every screen
 * composes; with `./gradlew :app:recordRoborazziDebug` it also writes the PNGs to
 * `app/build/outputs/roborazzi`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {

    @Test
    fun login() = capture("login") { Login() }

    @Test
    fun groups() = capture("groups") { Groups() }

    @Test
    fun groupsDark() = capture("groups-dark", dark = true) { Groups() }

    @Test
    fun groupExpenses() = capture("group-expenses") { Group(GroupTab.EXPENSES) }

    @Test
    fun groupBalances() = capture("group-balances") { Group(GroupTab.BALANCES) }

    @Test
    fun groupBalancesDark() = capture("group-balances-dark", dark = true) { Group(GroupTab.BALANCES) }

    @Test
    fun expenseEqual() = capture("expense-equal") { Expense(SampleData.equalDraft) }

    @Test
    @Config(qualifiers = "w411dp-h1500dp-420dpi")
    fun expensePerLine() = capture("expense-per-line") { Expense(SampleData.itemsDraft) }

    @Test
    @Config(qualifiers = "w411dp-h1500dp-420dpi")
    fun expensePerLineDark() = capture("expense-per-line-dark", dark = true) { Expense(SampleData.itemsDraft) }

    @Test
    fun profile() = capture("profile") { Profile() }

    @Test
    @Config(qualifiers = "w411dp-h1100dp-420dpi")
    fun groupSettings() = capture("group-settings") { Settings() }

    @Test
    fun join() = capture("join") { Join() }

    @Test
    @Config(qualifiers = "w360dp-h1700dp-420dpi")
    fun expenseOnASmallPhoneWithTheLargestFont() {
        RuntimeEnvironment.setFontScale(2f)
        capture("expense-font-scale-2") { Expense(SampleData.equalDraft) }
    }

    @Test
    @Config(qualifiers = "w360dp-h1200dp-420dpi")
    fun groupOnASmallPhoneWithTheLargestFont() {
        RuntimeEnvironment.setFontScale(2f)
        capture("group-font-scale-2") { Group(GroupTab.EXPENSES) }
    }

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            SplitTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = SplitTheme.colors.desk, content = content)
            }
        }
    }

    @Composable
    private fun Login() {
        AuthScreen(
            state = AuthUiState(email = "sanne@example.nl", password = "geheim-wachtwoord", serverUrl = "https://split.example.org"),
            onModeChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onServerUrlChange = {},
            onToggleAdvanced = {},
            onSubmit = {},
        )
    }

    @Composable
    private fun Groups() {
        GroupsScreen(
            state = GroupsUiState(isLoading = false, groups = SampleData.groups),
            onOpenGroup = {},
            onCreateGroup = { _, _ -> },
            onJoinWithCode = {},
            onOpenProfile = {},
            onRefresh = {},
            onRetry = {},
            onMessageShown = {},
        )
    }

    @Composable
    private fun Group(tab: GroupTab) {
        GroupScreen(
            state = GroupUiState(isLoading = false, group = SampleData.group, expenses = SampleData.expenses),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onOpenSettings = {},
            onAddExpense = {},
            onEditExpense = {},
            onRefresh = {},
            onRetry = {},
            onMarkAsPaid = {},
            onCopyIban = {},
            onSettlementChange = {},
            onSettlementSave = {},
            onSettlementDismiss = {},
            onDeleteSettlement = {},
            onMessageShown = {},
            initialTab = tab,
        )
    }

    @Composable
    private fun Expense(draft: ExpenseDraft) {
        ExpenseScreen(
            state = ExpenseUiState(
                isEditing = false,
                isLoading = false,
                group = SampleData.group,
                draft = draft,
                scanEnabled = true,
            ),
            onBack = {},
            onRetry = {},
            onDraftChange = {},
            onSave = {},
            onDelete = {},
            receiptActions = ReceiptActions(newPhotoUri = { Uri.EMPTY }, onPhoto = {}, onCameraUnavailable = {}),
            onMessageShown = {},
        )
    }

    @Composable
    private fun Profile() {
        ProfileScreen(
            state = ProfileUiState(
                isLoading = false,
                user = SampleData.user,
                name = SampleData.user.name,
                iban = "NL91 ABNA 0417 1643 00",
            ),
            onBack = {},
            onRetry = {},
            onNameChange = {},
            onIbanChange = {},
            onSave = {},
            onLogout = {},
            onDeleteAccount = {},
            onMessageShown = {},
        )
    }

    @Composable
    private fun Settings() {
        GroupSettingsScreen(
            state = GroupSettingsUiState(
                isLoading = false,
                group = SampleData.group,
                inviteLink = "https://split.example.org/join/${SampleData.group.inviteCode}",
            ),
            onBack = {},
            onRetry = {},
            onRename = {},
            onRecolor = {},
            onAddMember = {},
            onRenameMember = { _, _ -> },
            onRemoveMember = {},
            onShareInvite = {},
            onLeave = {},
            onMessageShown = {},
        )
    }

    @Composable
    private fun Join() {
        val lotte = UnclaimedMember("m4", "Lotte")
        JoinScreen(
            state = JoinUiState(
                code = SampleData.group.inviteCode,
                invite = InvitePreview(
                    groupId = SampleData.group.id,
                    name = SampleData.group.name,
                    color = SampleData.group.color,
                    memberCount = 4,
                    alreadyMember = false,
                    unclaimedMembers = listOf(lotte, UnclaimedMember("m5", "Opa Kees")),
                ),
                identity = Identity.ExistingMember(lotte.id),
            ),
            onBack = {},
            onCodeChange = {},
            onLookUp = {},
            onIdentityChange = {},
            onJoin = {},
        )
    }
}
