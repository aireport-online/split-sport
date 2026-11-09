package online.aireport.split.core

import kotlin.math.abs

private const val NBSP = ' '

// Unlike JavaScript's, Java's \s leaves out the non-breaking spaces that formatted amounts contain.
private val AMOUNT_NOISE = Regex("[\\s  €$£]")
private val SEPARATORS = Regex("[.,]")
private val PLAIN_AMOUNT = Regex("^\\d+(\\.\\d{0,2})?$")

/**
 * An amount as a till prints it, the Dutch way and without sign or euro sign: 123456 -> "1.234,56".
 * Used on receipt lines.
 */
fun formatAmount(cents: Long): String {
    val absolute = abs(cents)
    val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    val fraction = (absolute % 100).toString().padStart(2, '0')
    return "$whole,$fraction"
}

/**
 * An amount with the euro sign, for totals, headline figures and sentences: "€ 1.234,56".
 * The spaces are non-breaking so an amount never wraps.
 */
fun formatCents(cents: Long): String = "€$NBSP${if (cents < 0) "-" else ""}${formatAmount(cents)}"

/** A balance on a receipt line; it always has its sign: "+ 24,95", "− 21,60", and "0,00" for zero. */
fun formatSignedAmount(cents: Long): String = sign(cents) + formatAmount(cents)

/** A balance as a headline figure: "+ € 109,55", "− € 8,00", and "€ 0,00" for zero. */
fun formatSignedCents(cents: Long): String = sign(cents) + formatCents(abs(cents))

private fun sign(cents: Long): String = when {
    cents > 0 -> "+$NBSP"
    cents < 0 -> "−$NBSP"
    else -> ""
}

/** Formats cents for an amount text field, without the euro sign: 1250 -> "12,50". */
fun formatAmountInput(cents: Long): String = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"

/**
 * Parses what a person typed as an amount ("12,50", "12.5", "1.234,56", "€ 12") into integer
 * cents. Returns null when it is not a valid non-negative amount. A port of `parseAmount` in
 * `packages/core/src/money.ts`.
 */
fun parseAmount(input: String): Long? {
    var text = input.replace(AMOUNT_NOISE, "")
    if (text.isEmpty()) return null
    val decimalAt = maxOf(text.lastIndexOf(','), text.lastIndexOf('.'))
    if (decimalAt != -1) {
        val decimals = text.length - decimalAt - 1
        val separator = text[decimalAt]
        // "1.234" or "1,234,567" is a thousands separator, "12,5" / "12.50" a decimal one.
        val isDecimal = decimals <= 2 && text.count { it == separator } == 1
        text = when {
            isDecimal -> text.substring(0, decimalAt).replace(SEPARATORS, "") + "." + text.substring(decimalAt + 1)
            decimals == 3 -> text.replace(SEPARATORS, "")
            else -> return null
        }
    }
    if (!PLAIN_AMOUNT.matches(text)) return null
    val whole = text.substringBefore('.').toLongOrNull() ?: return null
    val fraction = text.substringAfter('.', "").padEnd(2, '0').toLong()
    // Anything beyond what a Long holds in cents is not an amount anybody means.
    if (whole > Long.MAX_VALUE / 100 - 1) return null
    return whole * 100 + fraction
}
