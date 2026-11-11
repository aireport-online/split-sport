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
        XCTAssertEqual(day.monthHeading, "NOVEMBER 2026")
        XCTAssertEqual(CalendarDay(year: 2025, month: 3, day: 1).monthHeading, "MAART 2025")
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
    func testColourRolesMatchTheDesign() {
        XCTAssertEqual(Palette.desk, ThemedColor(light: 0xE8E5DE, dark: 0x171614))
        XCTAssertEqual(Palette.paper, ThemedColor(light: 0xFBF8F1, dark: 0x24221F))
        XCTAssertEqual(Palette.ink, ThemedColor(light: 0x141414, dark: 0xF4F0E7))
        XCTAssertEqual(Palette.onInk, ThemedColor(light: 0xFBF8F1, dark: 0x171614))
        XCTAssertEqual(Palette.mutedInk, ThemedColor(light: 0x615D55, dark: 0xA9A499))
        XCTAssertEqual(Palette.perforation, ThemedColor(light: 0xB5B0A5, dark: 0x4A463F))
        XCTAssertEqual(Palette.stamp, ThemedColor(light: 0xD0342C, dark: 0xFF6B5E))
        XCTAssertEqual(Palette.highlighterInk, HexColor(0x141414))
    }

    /// The asset catalog repeats the ink colour as the accent for the system
    /// (alerts, menus); this keeps it equal to the palette.
    func testAccentColorAssetIsTheInk() throws {
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
        XCTAssertEqual(ThemedColor(light: try hex(light), dark: try hex(dark)), Palette.ink)
    }

    func testHighlightersMatchTheDesignTable() {
        // In palette order: this order decides every member's colour (docs/design.md).
        let table: [(GroupColor, UInt32)] = [
            (.amber, 0xFFF04A), (.rose, 0xFF8CCF), (.green, 0x8AF0AE), (.coral, 0xFFAE5C),
            (.blue, 0x8FD4FF), (.lime, 0xD2F25A), (.teal, 0x74E6DA), (.slate, 0xD8D4C9),
        ]
        XCTAssertEqual(table.map(\.0), GroupColor.allCases)
        XCTAssertEqual(GroupColor.allCases.map(\.rawValue), ["amber", "rose", "green", "coral", "blue", "lime", "teal", "slate"])
        for (color, hex) in table {
            XCTAssertEqual(color.highlighter, HexColor(hex), color.rawValue)
        }
        XCTAssertEqual(Set(GroupColor.allCases.map(\.title)).count, 8)
    }

    func testMemberColourWrapsAroundThePalette() {
        XCTAssertEqual(GroupColor.palette(at: 0), .amber)
        XCTAssertEqual(GroupColor.palette(at: 1), .rose)
        XCTAssertEqual(GroupColor.palette(at: 7), .slate)
        XCTAssertEqual(GroupColor.palette(at: 8), .amber)
        XCTAssertEqual(GroupColor.palette(at: 10), .green)
        XCTAssertEqual(Fixtures.group.party(Fixtures.chris.id).highlighter, .green)
        XCTAssertNil(Fixtures.group.party(Fixtures.pot.id).highlighter, "The pot is not a person and has no highlighter")
        XCTAssertEqual(Fixtures.group.party("unknown").name, "–")
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

    func testCountsAndIBAN() {
        XCTAssertEqual(Wording.memberCount(1), "1 teamlid")
        XCTAssertEqual(Wording.memberCount(5), "5 teamleden")
        XCTAssertEqual(Wording.teamLine(sport: .hockey, club: "MHC Oostpoort", season: "2026/27"), "Hockey · MHC Oostpoort · 2026/27")
        XCTAssertEqual(Wording.teamLine(sport: .voetbal, club: nil, season: "2026/27"), "Voetbal · 2026/27")
        XCTAssertEqual(Wording.teamLine(sport: .overig, club: "", season: nil), "Overig")
        XCTAssertEqual(Wording.chargeTotal(count: 5, amountCents: 2500), "5 × €\u{00A0}25,00 = €\u{00A0}125,00")
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
        XCTAssertEqual(try code("splitsport://join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(try code("splitsport://join/abcd2345"), "ABCD2345")
        XCTAssertEqual(try code("splitsport:///join/ABCD2345"), "ABCD2345")
        XCTAssertNil(try code("split://join/ABCD2345"), "The scheme of the other app is not ours")
        XCTAssertEqual(try code("https://split.example.nl/join/ABCD2345"), "ABCD2345")
        XCTAssertEqual(try code("http://localhost:8787/join/ABCD2345/"), "ABCD2345")
        XCTAssertNil(try code("splitsport://join"))
        XCTAssertNil(try code("splitsport://groups/123"))
        XCTAssertNil(try code("https://split.example.nl/"))
    }

    func testReadsTypedAndPastedInput() {
        XCTAssertEqual(InviteLink.code(fromInput: " abcd 2345 "), "ABCD2345")
        XCTAssertEqual(InviteLink.code(fromInput: "https://split.example.nl/join/abcd2345"), "ABCD2345")
        XCTAssertEqual(InviteLink.code(fromInput: "splitsport://join/ABCD2345"), "ABCD2345")
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
