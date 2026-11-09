import Foundation

public struct User: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public var name: String
    public var email: String
    public var iban: String?

    public init(id: String, name: String, email: String, iban: String?) {
        self.id = id
        self.name = name
        self.email = email
        self.iban = iban
    }
}

/// What register and login return.
public struct AuthResponse: Codable, Equatable, Sendable {
    public let token: String
    public let user: User

    public init(token: String, user: User) {
        self.token = token
        self.user = user
    }
}

/// `GET /config`: what this server supports.
public struct ServerConfig: Codable, Equatable, Sendable {
    public let receiptScanEnabled: Bool

    public init(receiptScanEnabled: Bool) {
        self.receiptScanEnabled = receiptScanEnabled
    }
}
