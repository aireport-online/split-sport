import XCTest
@testable import SplitKit

final class SplitMathTests: XCTestCase {
    func testAllocateGivesLeftoverCentsToLargestRemaindersThenEarlierIndex() throws {
        XCTAssertEqual(try SplitMath.allocate(1000, weights: [1, 1, 1]), [334, 333, 333])
        XCTAssertEqual(try SplitMath.allocate(1000, weights: [1, 2, 3]), [167, 333, 500])
        XCTAssertEqual(try SplitMath.allocate(1, weights: [1, 1, 1]), [1, 0, 0])
        XCTAssertEqual(try SplitMath.allocate(0, weights: [1, 2]), [0, 0])
    }

    func testAllocateNegatesANegativeTotal() throws {
        XCTAssertEqual(try SplitMath.allocate(-1000, weights: [1, 1, 1]), [-334, -333, -333])
    }

    func testAllocateNeedsAPositiveWeight() {
        XCTAssertThrowsError(try SplitMath.allocate(100, weights: [])) {
            XCTAssertEqual($0 as? SplitMath.SplitError, .noParticipants)
        }
        XCTAssertThrowsError(try SplitMath.allocate(100, weights: [0, 0]))
    }

    func testAllocateStaysExactAtTheLargestAmounts() throws {
        let parts = try SplitMath.allocate(SplitMath.maxAmountCents, weights: [SplitMath.maxAmountCents, 1, 99_999_998])
        XCTAssertEqual(parts.reduce(0, +), SplitMath.maxAmountCents)
    }

    func testItemsSpreadADiscountInProportion() throws {
        let split = Split.items([
            SplitItem(name: "Pizza", amountCents: 1500, memberIds: ["a"]),
            SplitItem(name: "Pasta", amountCents: 500, memberIds: ["b"]),
        ])
        let shares = try SplitMath.computeShares(amountCents: 1600, split: split)
        XCTAssertEqual(shares, [.init(memberId: "a", amountCents: 1200), .init(memberId: "b", amountCents: 400)])
    }

    func testWeightAboveTheMaximumIsRejected() {
        let split = Split.shares(weights: [.init(memberId: "a", weight: SplitMath.maxWeight + 1)])
        XCTAssertThrowsError(try SplitMath.computeShares(amountCents: 100, split: split)) {
            XCTAssertEqual($0 as? SplitMath.SplitError, .invalidWeight)
        }
    }

    func testSplitEncodesAsTheAPIExpects() throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        func json(_ split: Split) throws -> String {
            String(decoding: try encoder.encode(split), as: UTF8.self)
        }
        XCTAssertEqual(try json(.equal(memberIds: ["m1", "m2"])), #"{"memberIds":["m1","m2"],"mode":"equal"}"#)
        XCTAssertEqual(
            try json(.exact(amounts: [.init(memberId: "m1", amountCents: 1250)])),
            #"{"amounts":[{"amountCents":1250,"memberId":"m1"}],"mode":"exact"}"#
        )
        XCTAssertEqual(
            try json(.shares(weights: [.init(memberId: "m1", weight: 2)])),
            #"{"mode":"shares","weights":[{"memberId":"m1","weight":2}]}"#
        )
        XCTAssertEqual(
            try json(.items([SplitItem(name: "Pizza", amountCents: 1100, memberIds: ["m1"])])),
            #"{"items":[{"amountCents":1100,"memberIds":["m1"],"name":"Pizza"}],"mode":"items"}"#
        )
    }
}
