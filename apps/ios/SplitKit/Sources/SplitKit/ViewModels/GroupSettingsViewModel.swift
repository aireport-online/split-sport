import Foundation
import Observation

/// Group settings: name, colour, members, invitation and leaving.
@MainActor
@Observable
public final class GroupSettingsViewModel {
    public struct MemberRow: Identifiable, Equatable, Sendable {
        public let id: String
        /// The plain name, for the rename field.
        public let name: String
        /// "Anna (jij)" for yourself.
        public let displayName: String
        public let initials: String
        public let tint: GroupColor
        public let hasAccount: Bool
        /// Only members without an account can be renamed or removed.
        public var isEditable: Bool { !hasAccount }
    }

    public private(set) var detail: GroupDetail
    public var name: String
    public var newMemberName = ""
    public private(set) var isWorking = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI
    @ObservationIgnored private let serverURL: URL
    @ObservationIgnored private let onChange: (GroupDetail) -> Void

    /// - Parameter onChange: called with the group as the server returned it after every change.
    public init(api: SplitAPI, group: GroupDetail, serverURL: URL, onChange: @escaping (GroupDetail) -> Void) {
        self.api = api
        self.detail = group
        self.name = group.name
        self.serverURL = serverURL
        self.onChange = onChange
    }

    public var memberRows: [MemberRow] {
        detail.members.enumerated().map { index, member in
            MemberRow(
                id: member.id,
                name: member.name,
                displayName: Wording.memberName(member.name, isMe: member.id == detail.myMemberId),
                initials: Initials.of(member.name),
                tint: GroupColor.palette(at: index),
                hasAccount: member.hasAccount
            )
        }
    }

    /// The link "Deel uitnodiging" shares: `<server>/join/<code>`.
    public var inviteURL: URL {
        InviteLink.shareURL(server: serverURL, code: detail.inviteCode)
    }

    public var canSaveName: Bool {
        !name.trimmed.isEmpty && name.trimmed != detail.name && !isWorking
    }

    public var canAddMember: Bool {
        !newMemberName.trimmed.isEmpty && !isWorking
    }

    public func saveName() async {
        guard canSaveName else { return }
        await change { try await self.api.updateGroup(id: self.detail.id, name: self.name.trimmed, color: nil) }
        name = detail.name
    }

    public func setColor(_ color: GroupColor) async {
        guard color != detail.color else { return }
        await change { try await self.api.updateGroup(id: self.detail.id, name: nil, color: color) }
    }

    public func addMember() async {
        guard canAddMember else { return }
        let added = await change { try await self.api.addMember(groupId: self.detail.id, name: self.newMemberName.trimmed) }
        if added { newMemberName = "" }
    }

    public func renameMember(id: String, to newName: String) async {
        guard !newName.trimmed.isEmpty else { return }
        await change { try await self.api.renameMember(groupId: self.detail.id, memberId: id, name: newName.trimmed) }
    }

    public func removeMember(id: String) async {
        await change { try await self.api.removeMember(groupId: self.detail.id, memberId: id) }
    }

    /// Leaves the group; true when the group is gone for this person.
    public func leave() async -> Bool {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            try await api.leaveGroup(id: detail.id)
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }

    /// Runs one change that returns the updated group. True when it succeeded.
    @discardableResult
    private func change(_ request: () async throws -> GroupDetail) async -> Bool {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            detail = try await request()
            onChange(detail)
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }
}
