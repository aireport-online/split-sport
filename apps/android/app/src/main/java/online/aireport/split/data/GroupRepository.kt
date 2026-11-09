package online.aireport.split.data

import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.api.SplitApi
import online.aireport.split.data.model.CreateGroupRequest
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.data.model.JoinRequest
import online.aireport.split.data.model.MemberNameRequest
import online.aireport.split.data.model.UpdateGroupRequest

/** Groups, their members and invitations. */
class GroupRepository(
    private val gateway: ApiGateway,
    private val changes: GroupChanges,
) {
    suspend fun groups(): List<GroupSummary> = gateway.call { groups() }.groups

    suspend fun group(groupId: String): GroupDetail = gateway.call { group(groupId) }

    suspend fun createGroup(name: String, color: GroupColor): GroupDetail =
        changing { createGroup(CreateGroupRequest(name.trim(), color)) }

    suspend fun renameGroup(groupId: String, name: String): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(name = name.trim())) }

    suspend fun recolorGroup(groupId: String, color: GroupColor): GroupDetail =
        changing { updateGroup(groupId, UpdateGroupRequest(color = color)) }

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
