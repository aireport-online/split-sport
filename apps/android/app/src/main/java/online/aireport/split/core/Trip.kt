package online.aireport.split.core

import java.math.BigDecimal
import java.math.RoundingMode

/** The usual allowance per kilometre, in cents: € 0,23. */
const val DEFAULT_RATE_CENTS_PER_KM = 23L

private val KILOMETRES = Regex("^\\d{1,5}([.,]\\d)?$")

/**
 * The cost of a ride: the kilometres as typed ("84", "12,5") times a rate per kilometre, rounded
 * to whole cents. Null when the kilometres are not a positive number or the result is no amount
 * an expense can have.
 */
fun tripCostCents(kilometres: String, rateCentsPerKm: Long): Long? {
    val distance = parseKilometres(kilometres) ?: return null
    val cost = distance.multiply(BigDecimal(rateCentsPerKm)).setScale(0, RoundingMode.HALF_UP).toLong()
    return cost.takeIf { it in 1..MAX_AMOUNT_CENTS }
}

/** The kilometres as they are written in a description: "84", "12,5". Null when not a number. */
fun formatKilometres(kilometres: String): String? =
    parseKilometres(kilometres)?.stripTrailingZeros()?.toPlainString()?.replace('.', ',')

private fun parseKilometres(text: String): BigDecimal? {
    val trimmed = text.trim()
    if (!KILOMETRES.matches(trimmed)) return null
    return BigDecimal(trimmed.replace(',', '.')).takeIf { it.signum() > 0 }
}
