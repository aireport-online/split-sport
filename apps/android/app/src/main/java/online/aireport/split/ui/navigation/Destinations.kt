package online.aireport.split.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object AuthDestination

@Serializable
data object GroupsDestination

@Serializable
data class GroupDestination(val groupId: String)

@Serializable
data class GroupSettingsDestination(val groupId: String)

/** Adds an expense, or edits the one with [expenseId]. */
@Serializable
data class ExpenseDestination(val groupId: String, val expenseId: String? = null)

/** Joins a group; [code] is filled in when arriving by link. */
@Serializable
data class JoinDestination(val code: String? = null)

@Serializable
data object ProfileDestination
