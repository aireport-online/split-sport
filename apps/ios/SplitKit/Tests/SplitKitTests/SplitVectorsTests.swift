import XCTest
@testable import SplitKit

/// Runs the shared vectors of `spec/split-vectors.json`, read straight from the
/// repository so the three clients can never drift apart unnoticed.
final class SplitVectorsTests: XCTestCase {
    private struct VectorFile: Decodable {
        let cases: [Vector]
    }

    private struct Vector: Decodable {
        let name: String
        let amountCents: Int
        let split: Split
        let expected: [SplitMath.Share]?
        let error: String?
    }

    private func loadVectors() throws -> [Vector] {
        // …/apps/ios/SplitKit/Tests/SplitKitTests/SplitVectorsTests.swift → repository root.
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { root.deleteLastPathComponent() }
        let file = root.appendingPathComponent("spec").appendingPathComponent("split-vectors.json")
        return try JSONDecoder().decode(VectorFile.self, from: Data(contentsOf: file)).cases
    }

    func testEveryVector() throws {
        let vectors = try loadVectors()
        XCTAssertGreaterThanOrEqual(vectors.count, 20, "The vector file looks truncated")

        for vector in vectors {
            let result = Result { try SplitMath.computeShares(amountCents: vector.amountCents, split: vector.split) }
            switch (result, vector.expected, vector.error) {
            case (.success(let shares), let expected?, nil):
                XCTAssertEqual(shares, expected, vector.name)
                XCTAssertEqual(shares.reduce(0) { $0 + $1.amountCents }, vector.amountCents, vector.name)
            case (.failure(let error), nil, let code?):
                XCTAssertEqual((error as? SplitMath.SplitError)?.rawValue, code, vector.name)
            default:
                XCTFail("\(vector.name): got \(result), expected \(String(describing: vector.expected)) / \(String(describing: vector.error))")
            }
        }
    }

    func testVectorsCoverEveryModeAndError() throws {
        let vectors = try loadVectors()
        XCTAssertEqual(Set(vectors.map(\.split.mode)), Set(SplitMode.allCases))
        let knownCodes = Set(SplitMath.SplitError.allCases.map(\.rawValue))
        XCTAssertTrue(Set(vectors.compactMap(\.error)).isSubset(of: knownCodes))
    }
}
