import Foundation
import Observation

/// "Inloggen" / "Account maken".
@MainActor
@Observable
public final class AuthViewModel {
    public enum Mode: String, CaseIterable, Identifiable, Sendable {
        case login, register

        public var id: String { rawValue }

        public var title: String {
            switch self {
            case .login: return "Inloggen"
            case .register: return "Account maken"
            }
        }
    }

    public var mode: Mode = .login
    public var name = ""
    public var email = ""
    public var password = ""
    /// The "Server" field behind "Geavanceerd".
    public var serverAddress: String
    public private(set) var isSubmitting = false
    public var errorMessage: String?

    @ObservationIgnored private let session: AppSession

    public init(session: AppSession) {
        self.session = session
        self.serverAddress = session.serverURL.absoluteString
    }

    public var canSubmit: Bool {
        let hasName = mode == .login || !name.trimmed.isEmpty
        return hasName && !email.trimmed.isEmpty && !password.isEmpty && !isSubmitting
    }

    public func submit() async {
        guard canSubmit else { return }
        guard let serverURL = ServerAddress.parse(serverAddress) else {
            errorMessage = "Dit serveradres klopt niet."
            return
        }
        if mode == .register, password.count < 8 {
            errorMessage = "Kies een wachtwoord van minstens 8 tekens."
            return
        }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            switch mode {
            case .login:
                try await session.signIn(serverURL: serverURL, email: email.trimmed, password: password)
            case .register:
                try await session.register(serverURL: serverURL, name: name.trimmed, email: email.trimmed, password: password)
            }
        } catch {
            errorMessage = error.userMessage
        }
    }
}
