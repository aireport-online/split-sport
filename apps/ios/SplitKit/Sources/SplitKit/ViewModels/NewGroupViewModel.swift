import Foundation
import Observation

/// "Nieuwe groep".
@MainActor
@Observable
public final class NewGroupViewModel {
    public var name = ""
    public var color: GroupColor
    public private(set) var isSaving = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI

    /// - Parameter existingGroupCount: picks the first colour offered, so a person's groups look different.
    public init(api: SplitAPI, existingGroupCount: Int) {
        self.api = api
        self.color = GroupColor.palette(at: existingGroupCount)
    }

    public var canSave: Bool {
        !name.trimmed.isEmpty && !isSaving
    }

    /// Creates the group; nil when it failed (see `errorMessage`).
    public func create() async -> GroupDetail? {
        guard canSave else { return nil }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            return try await api.createGroup(name: name.trimmed, color: color)
        } catch {
            errorMessage = error.userMessage
            return nil
        }
    }
}
