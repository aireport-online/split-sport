package online.aireport.split.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val DUTCH = Locale.forLanguageTag("nl-NL")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", DUTCH)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", DUTCH)
private val FULL = DateTimeFormatter.ofPattern("d MMMM yyyy", DUTCH)

/** Parses an API calendar date (`YYYY-MM-DD`); null when it is not one. */
fun parseIsoDate(value: String): LocalDate? =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

/** "2 nov", with the year added when it is not [today]'s year: "2 nov 2025". */
fun formatShortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val formatter = if (date.year == today.year) DAY_MONTH else DAY_MONTH_YEAR
    // Some locale data abbreviates months with a trailing dot ("nov."); the design has none.
    return formatter.format(date).replace(".", "")
}

/** "2 november 2026", for date fields. */
fun formatLongDate(date: LocalDate): String = FULL.format(date)

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", DUTCH)

/** "NOVEMBER 2026": the heading above a month of expenses on a receipt. */
fun formatMonthHeading(date: LocalDate): String = MONTH_YEAR.format(date).uppercase(DUTCH)
