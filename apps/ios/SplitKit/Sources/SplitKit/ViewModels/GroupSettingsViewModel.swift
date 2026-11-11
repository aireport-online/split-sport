import Foundation
import Observation

/// Team settings: name, highlighter, sport, club, season, penningmeester, the
/// fine list, team members, invitation and leaving.
@MainActor
@Observable
public final class GroupSettingsViewModel {
    public struct MemberRow: Identifiable, Equatable, Sendable {
        public let party: Party
        public let isMe: Bool
        public let hasAccount: Bool

        public var id: String { party.id }
        /// Only members without an account can be renamed or removed.
        public var isEditable: Bool { !hasAccount }
    }

    public private(set) var detail: GroupDetail
    public var name: String
    public var club: String
    public var season: String
    public var newMemberName = ""
    /// The new line for the fine list.
    public var newFineName = ""
    public var newFineAmountText = ""
    public private(set) var isWorking = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI
    @ObservationIgnored private let serverURL: URL
    @ObservationIgnored private let onChange: (GroupDetail) -> Void

    /// - Parameter onChange: called with the team as the server returned it after every change.
    public init(api: SplitAPI, group: GroupDetail, serverURL: URL, onChange: @escaping (GroupDetail) -> Void) {
        self.api = api
        self.detail = group
        self.name = group.name
        self.club = group.club ?? ""
        self.season = group.season ?? ""
        self.serverURL = serverURL
        self.onChange = onChange
    }

    // MARK: Name, club and season

    public var canSaveDetails: Bool {
        let changed = name.trimmed != detail.name
            || club.trimmed != (detail.club ?? "")
            || season.trimmed != (detail.season ?? "")
        return changed && !name.trimmed.isEmpty && !isWorking
    }

    /// Saves the name, the club and the season; an emptied club or season is cleared.
    public func saveDetails() async {
        guard canSaveDetails else { return }
        let patch = GroupPatch(name: name.trimmed, club: club.trimmed, season: season.trimmed)
        await change { try await self.api.updateGroup(id: self.detail.id, patch: patch) }
        name = detail.name
        club = detail.club ?? ""
        season = detail.season ?? ""
    }

    public func setColor(_ color: GroupColor) async {
        guard color != detail.color else { return }
        await change { try await self.api.updateGroup(id: self.detail.id, patch: GroupPatch(color: color)) }
    }

    public func setSport(_ sport: Sport) async {
        guard sport != detail.sport else { return }
        await change { try await self.api.updateGroup(id: self.detail.id, patch: GroupPatch(sport: sport)) }
    }

    // MARK: Penningmeester

    /// The team members that can keep the pot: everyone but the pot itself.
    public var treasurerCandidates: [Party] {
        detail.parties.filter { !$0.isPot }
    }

    public func setTreasurer(memberId: String) async {
        guard memberId != detail.treasurerMemberId else { return }
        await change {
            try await self.api.updateGroup(id: self.detail.id, patch: GroupPatch(treasurerMemberId: memberId))
        }
    }

    // MARK: Boetelijst

    public var canAddFine: Bool {
        !newFineName.trimmed.isEmpty && Self.fineAmount(newFineAmountText) != nil && !isWorking
    }

    public func addFine() async {
        guard canAddFine, let amountCents = Self.fineAmount(newFineAmountText) else { return }
        let added = await change {
            try await self.api.addFine(groupId: self.detail.id, name: self.newFineName.trimmed, amountCents: amountCents)
        }
        if added {
            newFineName = ""
            newFineAmountText = ""
        }
    }

    /// Changes a line of the fine list. An unusable name or amount is reported, not sent.
    public func updateFine(id: String, name: String, amountText: String) async {
        guard !name.trimmed.isEmpty, let amountCents = Self.fineAmount(amountText) else {
            errorMessage = "Geef de boete een naam en een geldig bedrag."
            return
        }
        await change {
            try await self.api.updateFine(groupId: self.detail.id, fineId: id, name: name.trimmed, amountCents: amountCents)
        }
    }

    public func removeFine(id: String) async {
        await change { try await self.api.removeFine(groupId: self.detail.id, fineId: id) }
    }

    /// A fine is more than nothing and no more than the largest amount the server takes.
    private static func fineAmount(_ text: String) -> Int? {
        guard let cents = Money.parse(text), cents > 0, cents <= SplitMath.maxAmountCents else { return nil }
        return cents
    }

    // MARK: Team members

    /// The people of the team; the pot is not a team member.
    public var memberRows: [MemberRow] {
        detail.parties.filter { !$0.isPot }.map { party in
            MemberRow(
                party: party,
                isMe: party.id == detail.myMemberId,
                hasAccount: detail.member(party.id)?.hasAccount ?? false
            )
        }
    }

    public var canAddMember: Bool {
        !newMemberName.trimmed.isEmpty && !isWorking
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

    // MARK: Invitation and leaving

    /// The link "Deel uitnodiging" shares: `<server>/join/<code>`.
    public var inviteURL: URL {
        InviteLink.shareURL(server: serverURL, code: detail.inviteCode)
    }

    /// Leaves the team; true when the team is gone for this person.
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

    /// Runs one change that returns the updated team. True when it succeeded.
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
