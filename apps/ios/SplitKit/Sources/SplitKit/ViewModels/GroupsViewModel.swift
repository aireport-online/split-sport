import Foundation
import Observation

/// "Groepen": the home screen.
@MainActor
@Observable
public final class GroupsViewModel {
    public private(set) var groups: [GroupSummary] = []
    /// False until the first successful load, so the screen can tell "loading" from "no groups".
    public private(set) var hasLoaded = false
    public private(set) var isLoading = false
    public var errorMessage: String?

    @ObservationIgnored private let api: SplitAPI

    public init(api: SplitAPI) {
        self.api = api
    }

    public var totalBalanceCents: Int {
        groups.reduce(0) { $0 + $1.myBalanceCents }
    }

    /// The balance over all groups in words, e.g. "Je krijgt € 12,50".
    public var totalBalanceText: String {
        Wording.ownBalance(totalBalanceCents)
    }

    public var totalBalanceSign: Wording.BalanceSign {
        Wording.BalanceSign(cents: totalBalanceCents)
    }

    public func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            groups = try await api.groups()
            hasLoaded = true
            errorMessage = nil
        } catch {
            if let message = error.userMessage { errorMessage = message }
        }
    }
}
