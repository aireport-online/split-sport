import Foundation
import Observation

/// "Profiel": name, IBAN, logging out and deleting the account.
@MainActor
@Observable
public final class ProfileViewModel {
    public var name: String
    public var iban: String
    public private(set) var isWorking = false
    public var errorMessage: String?

    @ObservationIgnored private let session: AppSession

    public init(session: AppSession) {
        self.session = session
        self.name = session.user?.name ?? ""
        self.iban = Wording.groupedIBAN(session.user?.iban ?? "")
    }

    public var email: String {
        session.user?.email ?? ""
    }

    private var compactIBAN: String {
        iban.filter { !$0.isWhitespace }.uppercased()
    }

    public var hasChanges: Bool {
        name.trimmed != (session.user?.name ?? "") || compactIBAN != (session.user?.iban ?? "")
    }

    public var canSave: Bool {
        hasChanges && !name.trimmed.isEmpty && !isWorking
    }

    public func save() async {
        guard canSave else { return }
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            let user = try await session.api.updateMe(name: name.trimmed, iban: compactIBAN)
            session.userDidChange(user)
            name = user.name
            iban = Wording.groupedIBAN(user.iban ?? "")
        } catch {
            errorMessage = error.userMessage
        }
    }

    public func signOut() async {
        await session.signOut()
    }

    public func deleteAccount() async {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            try await session.deleteAccount()
        } catch {
            errorMessage = error.userMessage
        }
    }
}
