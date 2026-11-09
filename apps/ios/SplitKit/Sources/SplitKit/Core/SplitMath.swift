import Foundation

/// Splitting an amount of money over group members.
///
/// A port of `packages/core/src/split.ts`; `spec/split-vectors.json` keeps the
/// TypeScript, Kotlin and Swift implementations identical. All amounts are
/// integer cents. The app uses this for live previews; the server result is
/// always authoritative.
public enum SplitMath {
    public static let maxAmountCents = 99_999_999
    public static let maxWeight = 1_000_000

    /// One participant's part of an expense.
    public struct Share: Codable, Equatable, Sendable {
        public let memberId: String
        public let amountCents: Int

        public init(memberId: String, amountCents: Int) {
            self.memberId = memberId
            self.amountCents = amountCents
        }
    }

    /// Why a split cannot be computed. The raw values are the server's error codes.
    public enum SplitError: String, Error, CaseIterable, Sendable {
        case invalidAmount = "invalid_amount"
        case noParticipants = "no_participants"
        case duplicateMember = "duplicate_member"
        case invalidWeight = "invalid_weight"
        case exactSumMismatch = "exact_sum_mismatch"
        case invalidItem = "invalid_item"
        case itemUnassigned = "item_unassigned"

        /// The same Dutch text the server answers with for this code.
        public var message: String {
            switch self {
            case .invalidAmount: return "Vul een geldig bedrag in."
            case .noParticipants: return "Kies minstens één deelnemer."
            case .duplicateMember: return "Een deelnemer staat er dubbel in."
            case .invalidWeight: return "Vul geldige aandelen in."
            case .exactSumMismatch: return "De bedragen tellen niet op tot het totaal."
            case .invalidItem: return "Elke regel van de bon heeft een bedrag nodig."
            case .itemUnassigned: return "Wijs elke regel van de bon aan iemand toe."
            }
        }
    }

    /// Distributes `totalCents` proportionally to `weights` using the largest
    /// remainder method. Leftover cents go to the largest fractional parts; ties go
    /// to the earlier index. The result always sums to exactly `totalCents`.
    /// A negative total is allocated as its absolute value and negated.
    ///
    /// `Int` is 64 bits on every platform the app runs on; with amounts up to
    /// `maxAmountCents` the products stay far below `Int.max`.
    public static func allocate(_ totalCents: Int, weights: [Int]) throws -> [Int] {
        let weightSum = weights.reduce(0, +)
        guard !weights.isEmpty, weightSum > 0 else { throw SplitError.noParticipants }
        if totalCents < 0 {
            return try allocate(-totalCents, weights: weights).map { -$0 }
        }

        var result: [Int] = []
        var remainders: [(index: Int, remainder: Int)] = []
        var allocated = 0
        for (index, weight) in weights.enumerated() {
            let product = totalCents * weight
            let base = product / weightSum
            result.append(base)
            allocated += base
            remainders.append((index, product % weightSum))
        }
        remainders.sort { a, b in
            a.remainder == b.remainder ? a.index < b.index : a.remainder > b.remainder
        }
        var leftover = totalCents - allocated
        for entry in remainders {
            if leftover <= 0 { break }
            result[entry.index] += 1
            leftover -= 1
        }
        return result
    }

    /// Computes what every participant owes for an expense of `amountCents`.
    /// Shares are returned in order of first appearance and sum to `amountCents`.
    public static func computeShares(amountCents: Int, split: Split) throws -> [Share] {
        guard amountCents > 0, amountCents <= maxAmountCents else { throw SplitError.invalidAmount }

        switch split {
        case .equal(let memberIds):
            guard !memberIds.isEmpty else { throw SplitError.noParticipants }
            try assertUnique(memberIds)
            let parts = try allocate(amountCents, weights: memberIds.map { _ in 1 })
            return zip(memberIds, parts).map { Share(memberId: $0, amountCents: $1) }

        case .exact(let amounts):
            guard !amounts.isEmpty else { throw SplitError.noParticipants }
            try assertUnique(amounts.map(\.memberId))
            var sum = 0
            for entry in amounts {
                guard entry.amountCents >= 0 else { throw SplitError.invalidAmount }
                sum += entry.amountCents
            }
            guard sum == amountCents else { throw SplitError.exactSumMismatch }
            return amounts
                .filter { $0.amountCents > 0 }
                .map { Share(memberId: $0.memberId, amountCents: $0.amountCents) }

        case .shares(let weights):
            try assertUnique(weights.map(\.memberId))
            for entry in weights {
                guard (0...maxWeight).contains(entry.weight) else { throw SplitError.invalidWeight }
            }
            let active = weights.filter { $0.weight > 0 }
            guard !active.isEmpty else { throw SplitError.noParticipants }
            let parts = try allocate(amountCents, weights: active.map(\.weight))
            return zip(active, parts).map { Share(memberId: $0.memberId, amountCents: $1) }

        case .items(let items):
            guard !items.isEmpty else { throw SplitError.noParticipants }
            var order: [String] = []
            var subtotals: [String: Int] = [:]
            var itemsTotal = 0
            for item in items {
                guard item.amountCents > 0, item.amountCents <= maxAmountCents else { throw SplitError.invalidItem }
                guard !item.memberIds.isEmpty else { throw SplitError.itemUnassigned }
                try assertUnique(item.memberIds)
                itemsTotal += item.amountCents
                let parts = try allocate(item.amountCents, weights: item.memberIds.map { _ in 1 })
                for (memberId, part) in zip(item.memberIds, parts) {
                    if subtotals[memberId] == nil { order.append(memberId) }
                    subtotals[memberId, default: 0] += part
                }
            }
            guard itemsTotal <= maxAmountCents else { throw SplitError.invalidItem }
            // Whatever the receipt total differs from the items (tip, service charge,
            // discount) is spread in proportion to what everyone ordered.
            let weights = order.map { subtotals[$0] ?? 0 }
            let extra = try allocate(amountCents - itemsTotal, weights: weights)
            return order.indices.map { Share(memberId: order[$0], amountCents: weights[$0] + extra[$0]) }
        }
    }

    private static func assertUnique(_ ids: [String]) throws {
        guard Set(ids).count == ids.count else { throw SplitError.duplicateMember }
    }
}
