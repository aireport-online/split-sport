package online.aireport.split.ui.navigation

import kotlinx.serialization.Serializable
import online.aireport.split.data.model.ChargeType

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

/** "Boete geven" or "Inleg vragen" for a team. */
@Serializable
data class ChargeDestination(val groupId: String, val type: ChargeType)

/** Joins a team; [code] is filled in when arriving by link. */
@Serializable
data class JoinDestination(val code: String? = null)

@Serializable
data object ProfileDestination
