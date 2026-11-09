import Foundation
import Observation

/// "Deelnemen": join a group with an invite code.
@MainActor
@Observable
public final class JoinViewModel {
    /// The answer to "Wie ben jij?".
    public enum Identity: Hashable, Sendable {
        /// An existing member without account that turns out to be this person.
        case member(id: String)
        /// "Ik sta er niet tussen": join as a new member.
        case newMember
    }

    public var codeText: String
    /// The group behind the code, once it has been looked up.
    public private(set) var preview: InvitePreview?
    public var identity: Identity?
    public private(set) var isWorking = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI

    /// - Parameter code: a code that arrived by link; it is looked up by `lookUpIfPrefilled`.
    public init(api: SplitAPI, code: String? = nil) {
        self.api = api
        self.codeText = code ?? ""
    }

    private var code: String {
        InviteLink.code(fromInput: codeText)
    }

    public var canLookUp: Bool {
        !code.isEmpty && !isWorking
    }

    /// Whether the group has members without an account, so the person has to say who they are.
    public var needsIdentity: Bool {
        guard let preview else { return false }
        return !preview.alreadyMember && !preview.unclaimedMembers.isEmpty
    }

    public var canJoin: Bool {
        preview != nil && (!needsIdentity || identity != nil) && !isWorking
    }

    /// Looks up the code straight away when the screen was opened from a link.
    public func lookUpIfPrefilled() async {
        if preview == nil, !code.isEmpty { await lookUp() }
    }

    public func lookUp() async {
        guard canLookUp else { return }
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            preview = try await api.invite(code: code)
            identity = nil
        } catch {
            errorMessage = error.userMessage
        }
    }

    /// Back to entering a code.
    public func chooseOtherCode() {
        preview = nil
        identity = nil
        errorMessage = nil
    }

    /// Joins the group (or opens it when already a member); nil when it failed.
    public func join() async -> GroupDetail? {
        guard canJoin else { return nil }
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            var memberId: String?
            if needsIdentity, case .member(let id) = identity { memberId = id }
            return try await api.joinGroup(code: code, memberId: memberId)
        } catch {
            errorMessage = error.userMessage
            return nil
        }
    }
}
