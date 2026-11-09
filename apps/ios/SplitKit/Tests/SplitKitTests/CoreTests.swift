import XCTest
@testable import SplitKit

final class CalendarDayTests: XCTestCase {
    func testParsesAndPrintsISODates() {
        let day = CalendarDay(isoString: "2026-11-02")
        XCTAssertEqual(day, CalendarDay(year: 2026, month: 11, day: 2))
        XCTAssertEqual(day?.isoString, "2026-11-02")
        XCTAssertEqual(CalendarDay(year: 987, month: 1, day: 9).isoString, "0987-01-09")
    }

    func testRejectsDatesThatDoNotExist() {
        for text in ["", "2026-13-01", "2026-02-30", "2025-02-29", "2026-1-1", "02-11-2026", "2026-11-02T10:00", "abcd-ef-gh"] {
            XCTAssertNil(CalendarDay(isoString: text), text)
        }
        XCTAssertNotNil(CalendarDay(isoString: "2024-02-29"))
    }

    func testShortDutchText() {
        let day = CalendarDay(year: 2026, month: 11, day: 2)
        XCTAssertEqual(day.shortDutch(referenceYear: 2026), "2 nov")
        XCTAssertEqual(day.shortDutch(referenceYear: 2027), "2 nov 2026")
        XCTAssertEqual(CalendarDay(year: 2026, month: 3, day: 31).shortDutch(referenceYear: 2026), "31 mrt")
    }

    func testConvertsToAndFromDateInAnyTimeZone() throws {
        for identifier in ["Europe/Amsterdam", "Pacific/Kiritimati", "America/Los_Angeles", "UTC"] {
            var calendar = Calendar(identifier: .gregorian)
            calendar.timeZone = try XCTUnwrap(TimeZone(identifier: identifier))
            let day = CalendarDay(year: 2026, month: 3, day: 29)
            XCTAssertEqual(CalendarDay(date: day.date(calendar: calendar), calendar: calendar), day, identifier)
        }
    }

    func testCodableUsesThePlainString() throws {
        let data = try JSONEncoder().encode([CalendarDay(year: 2026, month: 1, day: 5)])
        XCTAssertEqual(String(decoding: data, as: UTF8.self), #"["2026-01-05"]"#)
        XCTAssertEqual(try JSONDecoder().decode([CalendarDay].self, from: data), [CalendarDay(year: 2026, month: 1, day: 5)])
        XCTAssertThrowsError(try JSONDecoder().decode([CalendarDay].self, from: Data(#"["2026-02-31"]"#.utf8)))
    }

    func testOrdering() {
        XCTAssertLessThan(CalendarDay(year: 2025, month: 12, day: 31), CalendarDay(year: 2026, month: 1, day: 1))
        XCTAssertLessThan(CalendarDay(year: 2026, month: 1, day: 1), CalendarDay(year: 2026, month: 1, day: 2))
    }
}

final class PaletteTests: XCTestCase {
    func testColourTokensMatchTheDesign() {
        XCTAssertEqual(Palette.ground, ThemedColor(light: 0xF3F5F7, dark: 0x101214))
        XCTAssertEqual(Palette.surface, ThemedColor(light: 0xFFFFFF, dark: 0x1A1D20))
        XCTAssertEqual(Palette.border, ThemedColor(light: 0xE6E6E6, dark: 0x2C3034))
        XCTAssertEqual(Palette.text, ThemedColor(light: 0x1B1D1F, dark: 0xECEEF0))
        XCTAssertEqual(Palette.mutedText, ThemedColor(light: 0x5C6064, dark: 0xA0A5AB))
        XCTAssertEqual(Palette.accent, ThemedColor(light: 0x1D5FD6, dark: 0x7FB0FF))
        XCTAssertEqual(Palette.onAccent, ThemedColor(light: 0xFFFFFF, dark: 0x06224F))
        XCTAssertEqual(Palette.positive, ThemedColor(light: 0x2F7A45, dark: 0x6FCF8B))
        XCTAssertEqual(Palette.negative, ThemedColor(light: 0xC41C29, dark: 0xFF8A8F))
    }

    /// The asset catalog repeats the accent colour for the system (alerts, the
    /// launch screen); this keeps it equal to the palette.
    func testAccentColorAssetMatchesThePalette() throws {
        struct ColorSet: Decodable {
            struct Entry: Decodable {
                struct Appearance: Decodable { let value: String }
                struct Value: Decodable { let components: [String: String] }
                let appearances: [Appearance]?
                let color: Value
            }
            let colors: [Entry]
        }
        // …/apps/ios/SplitKit/Tests/SplitKitTests/CoreTests.swift → …/apps/ios
        var ios = URL(fileURLWithPath: #filePath)
        for _ in 0..<4 { ios.deleteLastPathComponent() }
        let file = ios.appendingPathComponent("Split/Resources/Assets.xcassets/AccentColor.colorset/Contents.json")
        let colorSet = try JSONDecoder().decode(ColorSet.self, from: Data(contentsOf: file))

        func hex(_ entry: ColorSet.Entry) throws -> UInt32 {
            try ["red", "green", "blue"].reduce(0) { result, channel in
                let text = try XCTUnwrap(entry.color.components[channel])
                return (result << 8) | (try XCTUnwrap(UInt32(text.dropFirst(2), radix: 16)))
            }
        }
        let light = try XCTUnwrap(colorSet.colors.first { $0.appearances == nil })
        let dark = try XCTUnwrap(colorSet.colors.first { $0.appearances?.first?.value == "dark" })
        XCTAssertEqual(ThemedColor(light: try hex(light), dark: try hex(dark)), Palette.accent)
    }

    func testTintsMatchTheDesignTable() {
        // Key: background, ink, dark background, dark ink (docs/design.md).
        let table: [(GroupColor, UInt32, UInt32, UInt32, UInt32)] = [
            (.blue, 0xDCF1FF, 0x0A3A80, 0x12305C, 0xCFE6FF),
            (.amber, 0xFFF1CF, 0x7A3E00, 0x4A3206, 0xFFE2A6),
            (.teal, 0xD5F3F1, 0x0B4F4A, 0x0E3D3A, 0xBDEEEA),
            (.rose, 0xFFE1EC, 0x8A1846, 0x52142E, 0xFFC9DC),
            (.green, 0xDCFAE5, 0x0B4A22, 0x113D20, 0xC2F2D0),
            (.coral, 0xFFE4DC, 0x8A2B12, 0x52200F, 0xFFCDBF),
            (.lime, 0xECF6CC, 0x3F5200, 0x2F3B08, 0xE0F0A8),
            (.slate, 0xE9EDF2, 0x2F3A46, 0x2A3138, 0xD7DEE6),
        ]
        XCTAssertEqual(table.map(\.0), GroupColor.allCases, "The palette order decides member tints")
        for (color, background, ink, darkBackground, darkInk) in table {
            XCTAssertEqual(color.tint.background, ThemedColor(light: background, dark: darkBackground), color.rawValue)
            XCTAssertEqual(color.tint.ink, ThemedColor(light: ink, dark: darkInk), color.rawValue)
        }
    }

    func testMemberTintWrapsAroundThePalette() {
        XCTAssertEqual(GroupColor.palette(at: 0), .blue)
        XCTAssertEqual(GroupColor.palette(at: 7), .slate)
        XCTAssertEqual(GroupColor.palette(at: 8), .blue)
        XCTAssertEqual(GroupColor.palette(at: 10), .teal)
    }

    func testHexComponents() {
        let color = HexColor(0x1D5FD6)
        XCTAssertEqual(color.red, 29.0 / 255, accuracy: 1e-9)
        XCTAssertEqual(color.green, 95.0 / 255, accuracy: 1e-9)
        XCTAssertEqual(color.blue, 214.0 / 255, accuracy: 1e-9)
    }

    func testUnknownColourFallsBackToSlate() throws {
        let colors = try JSONDecoder().decode([GroupColor].self, from: Data(#"["teal","magenta"]"#.utf8))
        XCTAssertEqual(colors, [.teal, .slate])
    }

    func testInitials() {
        XCTAssertEqual(Initials.of("Weekendje Texel"), "WT")
        XCTAssertEqual(Initials.of("Anna"), "A")
        XCTAssertEqual(Initials.of("  huis  aan de gracht "), "HA")
        XCTAssertEqual(Initials.of("élise"), "É")
        XCTAssertEqual(Initials.of(""), "")
    }
}

final class WordingTests: XCTestCase {
    func testBalancePhrases() {
        XCTAssertEqual(Wording.ownBalance(1250), "Je krijgt €\u{00A0}12,50")
        XCTAssertEqual(Wording.ownBalance(-800), "Je bent €\u{00A0}8,00 verschuldigd")
        XCTAssertEqual(Wording.ownBalance(0), "Je staat quitte")
        XCTAssertEqual(Wording.memberBalance(1250), "krijgt €\u{00A0}12,50")
        XCTAssertEqual(Wording.memberBalance(-800), "betaalt €\u{00A0}8,00")
        XCTAssertEqual(Wording.memberBalance(0), "quitte")
    }

    func testExpenseImpact() {
        XCTAssertEqual(Wording.expenseImpact(666), "je krijgt €\u{00A0}6,66")
        XCTAssertEqual(Wording.expenseImpact(-333), "je betaalt €\u{00A0}3,33")
        XCTAssertNil(Wording.expenseImpact(0))
        XCTAssertNil(Wording.expenseImpact(nil))
    }

    func testNamesAndCounts() {
        XCTAssertEqual(Wording.memberName("Anna", isMe: true), "Anna (jij)")
        XCTAssertEqual(Wording.memberName("Bas", isMe: false), "Bas")
        XCTAssertEqual(Wording.memberCount(1), "1 lid")
        XCTAssertEqual(Wording.memberCount(4), "4 leden")
        XCTAssertEqual(Wording.groupedIBAN("NL91ABNA0417164300"), "NL91 ABNA 0417 1643 00")
        XCTAssertEqual(Wording.groupedIBAN(""), "")
    }
}

final class InviteLinkTests: XCTestCase {
    func testShareURL() throws {
        let server = try XCTUnwrap(URL(string: "https://split.example.nl"))
        XCTAssertEqual(InviteLink.shareURL(server: server, code: "ABCD2345").absoluteString, "https://split.example.nl/join/ABCD2345")
    }

    func testReadsTheCodeFromDeepLinksAndWebLinks() throws {
        func code(_ text: String) throws -> String? {
            InviteLink.code(from: try XCTUnwrap(URL(string: text)))
        }
        XCTAssertEqual(try code("split://join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(try code("split://join/abcd2345"), "ABCD2345")
        XCTAssertEqual(try code("split:///join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(try code("https://split.example.nl/join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(try code("http://localhost:8787/join/ABCD2345/"), "ABCD2345")
        XCTAssertNil(try code("split://join"))
        XCTAssertNil(try code("split://groups/123"))
        XCTAssertNil(try code("https://split.example.nl/"))
    }

    func testReadsTypedAndPastedInput() {
        XCTAssertEqual(InviteLink.code(fromInput: " abcd 2345 "), "ABCD2345")
        XCTAssertEqual(InviteLink.code(fromInput: "https://split.example.nl/join/abcd2345"), "ABCD2345")
        XCTAssertEqual(InviteLink.code(fromInput: "split://join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(InviteLink.code(fromInput: ""), "")
    }
}

final class ServerAddressTests: XCTestCase {
    func testNormalisesWhatWasTyped() {
        XCTAssertEqual(ServerAddress.parse("http://localhost:8787")?.absoluteString, "http://localhost:8787")
        XCTAssertEqual(ServerAddress.parse(" https://split.example.nl/ ")?.absoluteString, "https://split.example.nl")
        XCTAssertEqual(ServerAddress.parse("split.example.nl")?.absoluteString, "https://split.example.nl")
        XCTAssertEqual(ServerAddress.parse("https://split.example.nl/api/")?.absoluteString, "https://split.example.nl")
        XCTAssertEqual(ServerAddress.parse("localhost:8787")?.absoluteString, "http://localhost:8787")
        XCTAssertEqual(ServerAddress.parse("192.168.1.20:8787")?.absoluteString, "http://192.168.1.20:8787")
        XCTAssertEqual(ServerAddress.parse("https://example.nl/split")?.absoluteString, "https://example.nl/split")
    }

    func testRejectsUnusableAddresses() {
        XCTAssertNil(ServerAddress.parse(""))
        XCTAssertNil(ServerAddress.parse("   "))
        XCTAssertNil(ServerAddress.parse("ftp://example.nl"))
        XCTAssertNil(ServerAddress.parse("https://"))
        XCTAssertNil(ServerAddress.parse("niet een adres"))
    }
}
