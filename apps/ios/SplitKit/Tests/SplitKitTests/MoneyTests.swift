import XCTest
@testable import SplitKit

final class MoneyTests: XCTestCase {
    func testParsesCommaAndDotAsDecimalSeparator() {
        XCTAssertEqual(Money.parse("12,50"), 1250)
        XCTAssertEqual(Money.parse("12.50"), 1250)
        XCTAssertEqual(Money.parse("12,5"), 1250)
        XCTAssertEqual(Money.parse("12.5"), 1250)
        XCTAssertEqual(Money.parse("12"), 1200)
        XCTAssertEqual(Money.parse("0,05"), 5)
        XCTAssertEqual(Money.parse("12,"), 1200)
        XCTAssertEqual(Money.parse("0"), 0)
    }

    func testParsesThousandsSeparators() {
        XCTAssertEqual(Money.parse("1.234,56"), 123_456)
        XCTAssertEqual(Money.parse("1,234.56"), 123_456)
        XCTAssertEqual(Money.parse("1.234"), 123_400)
        XCTAssertEqual(Money.parse("1,234,567"), 123_456_700)
        XCTAssertEqual(Money.parse("1.234.567,8"), 123_456_780)
    }

    func testIgnoresCurrencySignsAndSpaces() {
        XCTAssertEqual(Money.parse("€ 12"), 1200)
        XCTAssertEqual(Money.parse(" € 1 234,50 "), 123_450)
        XCTAssertEqual(Money.parse("€\u{00A0}12,50"), 1250)
        XCTAssertEqual(Money.parse("$3.2"), 320)
    }

    func testRejectsWhatIsNotAnAmount() {
        for input in ["", "   ", "€", "abc", "-5", "12,345,6", "1.2.3", ",5", "12,3a", "1e3", "12.345.67", "٣"] {
            XCTAssertNil(Money.parse(input), "\"\(input)\" should not parse")
        }
        XCTAssertNil(Money.parse("99999999999999999999"))
        XCTAssertNil(Money.parse("90071992547409,92"))
        XCTAssertEqual(Money.parse("90071992547409,91"), 9_007_199_254_740_991)
    }

    func testFormatsDutchEuros() {
        XCTAssertEqual(Money.format(123_456), "€\u{00A0}1.234,56")
        XCTAssertEqual(Money.format(1250), "€\u{00A0}12,50")
        XCTAssertEqual(Money.format(5), "€\u{00A0}0,05")
        XCTAssertEqual(Money.format(0), "€\u{00A0}0,00")
        XCTAssertEqual(Money.format(100_000), "€\u{00A0}1.000,00")
        XCTAssertEqual(Money.format(99_999_999), "€\u{00A0}999.999,99")
        XCTAssertEqual(Money.format(123_456_789_012), "€\u{00A0}1.234.567.890,12")
        XCTAssertEqual(Money.format(-800), "€\u{00A0}-8,00")
    }

    func testFormatsSignedBalances() {
        XCTAssertEqual(Money.formatSigned(1250), "+\u{00A0}€\u{00A0}12,50")
        XCTAssertEqual(Money.formatSigned(-800), "−\u{00A0}€\u{00A0}8,00")
        XCTAssertEqual(Money.formatSigned(0), "€\u{00A0}0,00")
    }

    func testEditingTextParsesBackToTheSameAmount() {
        XCTAssertEqual(Money.editingText(123_456), "1234,56")
        XCTAssertEqual(Money.editingText(5), "0,05")
        for cents in [0, 1, 99, 100, 1250, 99_999, 100_000, 99_999_999] {
            XCTAssertEqual(Money.parse(Money.editingText(cents)), cents)
        }
    }
}
