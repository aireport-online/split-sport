package online.aireport.split.data

import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.api.SplitApi
import online.aireport.split.data.model.CreateGroupRequest
import online.aireport.split.data.model.FineRequest
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.data.model.JoinRequest
import online.aireport.split.data.model.MemberNameRequest
import online.aireport.split.data.model.Sport
import online.aireport.split.data.model.UpdateGroupRequest

/** What is asked when a team is made. */
data class NewTeam(
    val name: String,
    val color: GroupColor,
    val sport: Sport,
    /** Empty when the team has no club. */
    val club: String,
    val season: String,
)

/** Teams, their members, fine lists and invitations. */
class GroupRepository(
    private val gateway: ApiGateway,
    private val changes: GroupChanges,
) {
    suspend fun groups(): List<GroupSummary> = gateway.call { groups() }.groups

    suspend fun group(groupId: String): GroupDetail = gateway.call { group(groupId) }

    /** Creates the team with its pot; the caller becomes its penningmeester. */
    suspend fun createGroup(team: NewTeam): GroupDetail =
        changing {
            createGroup(
                CreateGroupRequest(
                    name = team.name.trim(),
                    color = team.color,
                    sport = team.sport,
                    club = team.club.trim().ifEmpty { null },
                    season = team.season.trim().ifEmpty { null },
                ),
            )
        }

    /** Changes the name, club and season; an empty club or season clears it. */
    suspend fun updateDetails(groupId: String, name: String, club: String, season: String): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(name = name.trim(), club = club.trim(), season = season.trim())) }

    suspend fun recolorGroup(groupId: String, color: GroupColor): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(color = color)) }

    suspend fun changeSport(groupId: String, sport: Sport): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(sport = sport)) }

    /** Hands the pot to another team member (never the pot itself). */
    suspend fun changeTreasurer(groupId: String, memberId: String): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(treasurerMemberId = memberId)) }

    suspend fun addFine(groupId: String, name: String, amountCents: Long): GroupDetail =
        changing { addFine(groupId, FineRequest(name.trim(), amountCents)) }

    suspend fun updateFine(groupId: String, fineId: String, name: String, amountCents: Long): GroupDetail =
        changing { updateFine(groupId, fineId, FineRequest(name.trim(), amountCents)) }

    suspend fun removeFine(groupId: String, fineId: String): GroupDetail =
        changing { removeFine(groupId, fineId) }

    suspend fun leaveGroup(groupId: String) = changing { leaveGroup(groupId) }

    suspend fun addMember(groupId: String, name: String): GroupDetail =
        changing { addMember(groupId, MemberNameRequest(name.trim())) }

    suspend fun renameMember(groupId: String, memberId: String, name: String): GroupDetail =
        changing { renameMember(groupId, memberId, MemberNameRequest(name.trim())) }

    suspend fun removeMember(groupId: String, memberId: String): GroupDetail =
        changing { removeMember(groupId, memberId) }

    suspend fun invite(code: String): InvitePreview = gateway.call { invite(code) }

    /** Joins as a new member, or as the existing member [claimMemberId] without an account. */
    suspend fun join(code: String, claimMemberId: String?): GroupDetail =
        changing { join(code, JoinRequest(claimMemberId)) }

    private suspend fun <T> changing(request: suspend SplitApi.() -> T): T =
        gateway.call(request).also { changes.notifyChanged() }
}
