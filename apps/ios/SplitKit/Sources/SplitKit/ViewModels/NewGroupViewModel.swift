import Foundation
import Observation

/// "Nieuw team".
@MainActor
@Observable
public final class NewGroupViewModel {
    public var name = ""
    public var sport = Sport.voetbal
    public var club = ""
    /// Prefilled with the running season, July to June.
    public var season: String
    public var color: GroupColor
    public private(set) var isSaving = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI

    /// - Parameter existingGroupCount: picks the first colour offered, so a person's teams look different.
    public init(api: SplitAPI, existingGroupCount: Int, today: CalendarDay = .today()) {
        self.api = api
        self.color = GroupColor.palette(at: existingGroupCount)
        self.season = Season.of(today)
    }

    public var canSave: Bool {
        !name.trimmed.isEmpty && !isSaving
    }

    /// Creates the team; nil when it failed (see `errorMessage`).
    public func create() async -> GroupDetail? {
        guard canSave else { return nil }
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            return try await api.createGroup(
                name: name.trimmed, color: color, sport: sport, club: club.trimmed, season: season.trimmed
            )
        } catch {
            errorMessage = error.userMessage
            return nil
        }
    }
}
