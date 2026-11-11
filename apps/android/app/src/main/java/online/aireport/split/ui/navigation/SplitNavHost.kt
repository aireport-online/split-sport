package online.aireport.split.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import online.aireport.split.data.model.ChargeType
import online.aireport.split.ui.auth.AuthRoute
import online.aireport.split.ui.charge.ChargeRoute
import online.aireport.split.ui.expense.ExpenseRoute
import online.aireport.split.ui.group.GroupRoute
import online.aireport.split.ui.groups.GroupsRoute
import online.aireport.split.ui.join.JoinRoute
import online.aireport.split.ui.profile.ProfileRoute
import online.aireport.split.ui.settings.GroupSettingsRoute

/**
 * The navigation graph. Signing in or out (also when the server answers 401 and the session is
 * dropped) replaces the whole back stack by the home or the login screen.
 */
@Composable
fun SplitNavHost(isSignedIn: Boolean, pendingInviteCode: String?, onInviteHandled: () -> Unit) {
    val navController = rememberNavController()
    val startDestination: Any = remember { if (isSignedIn) GroupsDestination else AuthDestination }

    LaunchedEffect(isSignedIn) {
        val onAuthScreen = navController.currentDestination?.hasRoute<AuthDestination>() == true
        if (isSignedIn && onAuthScreen) navController.replaceAllWith(GroupsDestination)
        if (!isSignedIn && !onAuthScreen) navController.replaceAllWith(AuthDestination)
    }

    // An invitation link waits until someone is signed in.
    LaunchedEffect(isSignedIn, pendingInviteCode) {
        if (isSignedIn && pendingInviteCode != null) {
            navController.navigate(JoinDestination(pendingInviteCode))
            onInviteHandled()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<AuthDestination> { AuthRoute() }

        composable<GroupsDestination> {
            GroupsRoute(
                onOpenGroup = { groupId -> navController.navigate(GroupDestination(groupId)) },
                onJoinWithCode = { navController.navigate(JoinDestination()) },
                onOpenProfile = { navController.navigate(ProfileDestination) },
            )
        }

        composable<GroupDestination> { entry ->
            val groupId = entry.toRoute<GroupDestination>().groupId
            GroupRoute(
                groupId = groupId,
                onBack = navController::goBack,
                onOpenSettings = { navController.navigate(GroupSettingsDestination(groupId)) },
                onAddExpense = { navController.navigate(ExpenseDestination(groupId)) },
                onEditExpense = { expenseId -> navController.navigate(ExpenseDestination(groupId, expenseId)) },
                onGiveFine = { navController.navigate(ChargeDestination(groupId, ChargeType.FINE)) },
                onAskContribution = { navController.navigate(ChargeDestination(groupId, ChargeType.CONTRIBUTION)) },
            )
        }

        composable<GroupSettingsDestination> { entry ->
            GroupSettingsRoute(
                groupId = entry.toRoute<GroupSettingsDestination>().groupId,
                onBack = navController::goBack,
                onLeftGroup = { navController.popBackStack(GroupsDestination, inclusive = false) },
            )
        }

        composable<ExpenseDestination> { entry ->
            val destination = entry.toRoute<ExpenseDestination>()
            ExpenseRoute(
                groupId = destination.groupId,
                expenseId = destination.expenseId,
                onDone = navController::goBack,
            )
        }

        composable<ChargeDestination> { entry ->
            val destination = entry.toRoute<ChargeDestination>()
            ChargeRoute(groupId = destination.groupId, type = destination.type, onDone = navController::goBack)
        }

        composable<JoinDestination> { entry ->
            JoinRoute(
                initialCode = entry.toRoute<JoinDestination>().code,
                onBack = navController::goBack,
                onOpenGroup = { groupId ->
                    navController.navigate(GroupDestination(groupId)) {
                        popUpTo(GroupsDestination)
                    }
                },
            )
        }

        composable<ProfileDestination> { ProfileRoute(onBack = navController::goBack) }
    }
}

/** Goes back once, also when the back arrow is tapped twice during the transition. */
private fun NavHostController.goBack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

private fun NavHostController.replaceAllWith(destination: Any) {
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
    }
}
