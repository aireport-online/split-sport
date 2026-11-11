package online.aireport.split.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The highlighter a team is marked with. The order is the order of `docs/design.md`: a member's
 * highlighter is their position in the team modulo 8, in this order.
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
enum class MemberKind {
    /** A person in the team. */
    @SerialName("player") PLAYER,

    /** The team pot (teamkas): one per team, never a person. */
    @SerialName("pot") POT,
}

@Serializable
data class Member(
    val id: String,
    val name: String,
    val kind: MemberKind = MemberKind.PLAYER,
    /** Null for a member that was added by name and has no account (yet). */
    val userId: String? = null,
    /** IBAN of the linked account, so others know where to pay. */
    val iban: String? = null,
) {
    val isPot: Boolean get() = kind == MemberKind.POT
}

@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val color: GroupColor,
    val sport: Sport = Sport.OVERIG,
    val club: String? = null,
    val season: String? = null,
    /** Team members, not counting the pot. */
    val memberCount: Int,
    val myMemberId: String,
    val myBalanceCents: Long,
)

@Serializable
data class GroupList(val groups: List<GroupSummary>)

@Serializable
data class Balance(
    val memberId: String,
    /** The total. Positive: gets money back. Negative: owes. */
    val balanceCents: Long,
    /**
     * The part that is with the team pot. Negative: still owes the pot (contributions, fines);
     * positive: the pot owes them. For the pot itself: what members still owe it.
     */
    val potCents: Long,
    /** The part that is between team members, from ordinary shared expenses. */
    val peerCents: Long,
)

@Serializable
data class Transfer(val fromMemberId: String, val toMemberId: String, val amountCents: Long)

@Serializable
data class PotStatus(
    /** The kasstand: money actually in the pot. Unpaid fines and contributions are not cash. */
    val cashCents: Long,
    /** What members still have to pay into the pot. */
    val toReceiveCents: Long,
    /** What the pot still has to pay out to members who advanced something. */
    val toPayCents: Long,
)

/** A line of the team's fine list (boetelijst). */
@Serializable
data class Fine(val id: String, val name: String, val amountCents: Long)

/** How many fines a member got and what they add up to. */
@Serializable
data class FineStat(val memberId: String, val count: Int, val totalCents: Long)

@Serializable
data class GroupDetail(
    val id: String,
    val name: String,
    val color: GroupColor,
    val sport: Sport = Sport.OVERIG,
    val club: String? = null,
    val season: String? = null,
    val inviteCode: String,
    val myMemberId: String,
    /** The team pot, also the last entry of [members]. */
    val potMemberId: String,
    /** The member who keeps the pot's money; paying the pot is a transfer to their IBAN. */
    val treasurerMemberId: String? = null,
    /** Team members in the order they joined, followed by the pot. */
    val members: List<Member>,
    val balances: List<Balance>,
    /** Suggested payments: first everyone with the pot, then team members among themselves. */
    val transfers: List<Transfer>,
    val pot: PotStatus,
    val fines: List<Fine>,
    /** Fines per member, most first; only members with at least one fine. */
    val fineStats: List<FineStat>,
    val totalSpentCents: Long,
) {
    /** The people in the team: everyone but the pot. */
    val players: List<Member> get() = members.filterNot { it.isPot }

    val treasurer: Member? get() = treasurerMemberId?.let(::member)

    /** The suggested payments into and out of the pot. */
    val potTransfers: List<Transfer> get() = transfers.filter { it.involves(potMemberId) }

    /** The suggested payments between team members. */
    val peerTransfers: List<Transfer> get() = transfers.filterNot { it.involves(potMemberId) }

    fun member(id: String): Member? = members.firstOrNull { it.id == id }

    /**
     * The member's position in the team, which decides their highlighter; 0 when unknown. The
     * pot comes last, so it does not shift anybody's colour.
     */
    fun memberPosition(id: String): Int = members.indexOfFirst { it.id == id }.coerceAtLeast(0)

    private fun Transfer.involves(memberId: String) = fromMemberId == memberId || toMemberId == memberId
}

@Serializable
data class UnclaimedMember(val id: String, val name: String)

@Serializable
data class InvitePreview(
    val groupId: String,
    val name: String,
    val color: GroupColor,
    val sport: Sport = Sport.OVERIG,
    val club: String? = null,
    val memberCount: Int,
    val alreadyMember: Boolean,
    /** Members without an account that the invitee may claim as themselves. */
    val unclaimedMembers: List<UnclaimedMember>,
)
