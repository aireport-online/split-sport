package online.aireport.split.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The highlighter a group is marked with. The order is the order of `docs/design.md`: a member's
 * highlighter is their position in the group modulo 8, in this order.
 */
@Serializable
enum class GroupColor {
    @SerialName("amber") AMBER,
    @SerialName("rose") ROSE,
    @SerialName("green") GREEN,
    @SerialName("coral") CORAL,
    @SerialName("blue") BLUE,
    @SerialName("lime") LIME,
    @SerialName("teal") TEAL,
    @SerialName("slate") SLATE,
}

@Serializable
data class Member(
    val id: String,
    val name: String,
    /** Null for a member that was added by name and has no account (yet). */
    val userId: String? = null,
    /** IBAN of the linked account, so others know where to pay. */
    val iban: String? = null,
)

@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val color: GroupColor,
    val memberCount: Int,
    val myMemberId: String,
    val myBalanceCents: Long,
)

@Serializable
data class GroupList(val groups: List<GroupSummary>)

@Serializable
data class Balance(
    val memberId: String,
    /** Positive: the group owes this member. Negative: this member owes the group. */
    val balanceCents: Long,
)

@Serializable
data class Transfer(val fromMemberId: String, val toMemberId: String, val amountCents: Long)

@Serializable
data class GroupDetail(
    val id: String,
    val name: String,
    val color: GroupColor,
    val inviteCode: String,
    val myMemberId: String,
    val members: List<Member>,
    val balances: List<Balance>,
    /** Suggested payments that settle every balance. */
    val transfers: List<Transfer>,
    val totalSpentCents: Long,
) {
    fun member(id: String): Member? = members.firstOrNull { it.id == id }

    /** The member's position in the group, which decides their highlighter; 0 when unknown. */
    fun memberPosition(id: String): Int = members.indexOfFirst { it.id == id }.coerceAtLeast(0)
}

@Serializable
data class CreateGroupRequest(val name: String, val color: GroupColor)

@Serializable
data class UpdateGroupRequest(val name: String? = null, val color: GroupColor? = null)

@Serializable
data class MemberNameRequest(val name: String)

@Serializable
data class UnclaimedMember(val id: String, val name: String)

@Serializable
data class InvitePreview(
    val groupId: String,
    val name: String,
    val color: GroupColor,
    val memberCount: Int,
    val alreadyMember: Boolean,
    /** Members without an account that the invitee may claim as themselves. */
    val unclaimedMembers: List<UnclaimedMember>,
)

@Serializable
data class JoinRequest(val memberId: String? = null)
