import Foundation

/// How an expense is divided, as sent to the server and fed to `computeShares`.
public enum Split: Equatable, Sendable {
    case equal(memberIds: [String])
    case exact(amounts: [ExactAmount])
    case shares(weights: [ShareWeight])
    case items([SplitItem])

    public struct ExactAmount: Codable, Equatable, Sendable {
        public var memberId: String
        public var amountCents: Int

        public init(memberId: String, amountCents: Int) {
            self.memberId = memberId
            self.amountCents = amountCents
        }
    }

    public struct ShareWeight: Codable, Equatable, Sendable {
        public var memberId: String
        public var weight: Int

        public init(memberId: String, weight: Int) {
            self.memberId = memberId
            self.weight = weight
        }
    }

    public var mode: SplitMode {
        switch self {
        case .equal: return .equal
        case .exact: return .exact
        case .shares: return .shares
        case .items: return .items
        }
    }
}

extension Split: Codable {
    private enum CodingKeys: String, CodingKey {
        case mode, memberIds, amounts, weights, items
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        switch try container.decode(SplitMode.self, forKey: .mode) {
        case .equal:
            self = .equal(memberIds: try container.decode([String].self, forKey: .memberIds))
        case .exact:
            self = .exact(amounts: try container.decode([ExactAmount].self, forKey: .amounts))
        case .shares:
            self = .shares(weights: try container.decode([ShareWeight].self, forKey: .weights))
        case .items:
            self = .items(try container.decode([SplitItem].self, forKey: .items))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(mode, forKey: .mode)
        switch self {
        case .equal(let memberIds):
            try container.encode(memberIds, forKey: .memberIds)
        case .exact(let amounts):
            try container.encode(amounts, forKey: .amounts)
        case .shares(let weights):
            try container.encode(weights, forKey: .weights)
        case .items(let items):
            try container.encode(items, forKey: .items)
        }
    }
}
