import Foundation

/// Everything that can go wrong when talking to the server.
public enum APIError: Error, Equatable, Sendable {
    /// The server answered with a non-2xx status and `{"error": {code, message}}`.
    case server(status: Int, code: String, message: String)
    /// The token is no longer valid; the session has been told to return to the login screen.
    case sessionExpired
    /// The server could not be reached.
    case offline
    /// The server answered with something this app does not understand.
    case invalidResponse
    /// The request was cancelled, for example because its screen went away.
    case cancelled

    /// The stable machine code of a server error.
    public var code: String? {
        if case .server(_, let code, _) = self { return code }
        return nil
    }

    /// Dutch text to show to the user; nil for a cancelled request, which is not worth mentioning.
    public var message: String? {
        switch self {
        case .server(_, _, let message): return message
        case .sessionExpired: return "Je sessie is verlopen. Log opnieuw in."
        case .offline: return "Geen verbinding met de server. Controleer je internet en probeer het opnieuw."
        case .invalidResponse: return "De server gaf een onverwacht antwoord. Probeer het opnieuw."
        case .cancelled: return nil
        }
    }
}

public extension Error {
    /// The text to show for any error a view model catches; nil when there is nothing to show.
    var userMessage: String? {
        if let error = self as? APIError { return error.message }
        if self is CancellationError { return nil }
        return "Er ging iets mis. Probeer het opnieuw."
    }
}
