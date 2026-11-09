import Foundation

/// A calendar date without a time or time zone, as the API sends it (`YYYY-MM-DD`).
public struct CalendarDay: Hashable, Comparable, Sendable {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Parses `YYYY-MM-DD`; nil when the text is not an existing date.
    public init?(isoString: String) {
        let parts = isoString.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCIIDigit) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]),
              (1...12).contains(month), (1...CalendarDay.daysIn(month: month, year: year)).contains(day)
        else { return nil }
        self.init(year: year, month: month, day: day)
    }

    /// The day `date` falls on in `calendar` (the device's calendar by default).
    public init(date: Date, calendar: Calendar = .current) {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    public static func today(calendar: Calendar = .current) -> CalendarDay {
        CalendarDay(date: Date(), calendar: calendar)
    }

    public var isoString: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    /// Noon on this day in `calendar`, for use with a date picker.
    public func date(calendar: Calendar = .current) -> Date {
        let parts = DateComponents(year: year, month: month, day: day, hour: 12)
        return calendar.date(from: parts) ?? Date()
    }

    /// "2 nov"; the year is added when it differs from `referenceYear`.
    public func shortDutch(referenceYear: Int = CalendarDay.today().year) -> String {
        let text = "\(day) \(CalendarDay.dutchMonths[month - 1])"
        return year == referenceYear ? text : "\(text) \(year)"
    }

    /// "NOVEMBER 2026": the heading above the expenses of a month, as a till prints it.
    public var monthHeading: String {
        "\(CalendarDay.dutchMonthNames[month - 1].uppercased()) \(year)"
    }

    public static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    private static let dutchMonths = ["jan", "feb", "mrt", "apr", "mei", "jun", "jul", "aug", "sep", "okt", "nov", "dec"]
    private static let dutchMonthNames = [
        "januari", "februari", "maart", "april", "mei", "juni",
        "juli", "augustus", "september", "oktober", "november", "december",
    ]

    private static func daysIn(month: Int, year: Int) -> Int {
        switch month {
        case 2:
            let isLeap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
            return isLeap ? 29 : 28
        case 4, 6, 9, 11:
            return 30
        default:
            return 31
        }
    }
}

extension CalendarDay: Codable {
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let text = try container.decode(String.self)
        guard let day = CalendarDay(isoString: text) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Not a calendar date: \(text)")
        }
        self = day
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(isoString)
    }
}

private extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
