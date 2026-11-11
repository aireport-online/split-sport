package online.aireport.split.data.model

import kotlinx.serialization.Serializable

/** `club` and `season` are optional: null leaves them out. */
@Serializable
data class CreateGroupRequest(
    val name: String,
    val color: GroupColor,
    val sport: Sport,
    val club: String?,
    val season: String?,
)

/** Only what is not null is changed; an empty `club` or `season` clears it. */
@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val color: GroupColor? = null,
    val sport: Sport? = null,
    val club: String? = null,
    val season: String? = null,
    val treasurerMemberId: String? = null,
)

@Serializable
data class FineRequest(val name: String, val amountCents: Long)

@Serializable
data class MemberNameRequest(val name: String)

@Serializable
data class JoinRequest(val memberId: String? = null)
