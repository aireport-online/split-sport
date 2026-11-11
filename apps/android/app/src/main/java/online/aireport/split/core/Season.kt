package online.aireport.split.core

import java.time.LocalDate
import java.time.Month

/** The season a date falls in, running from July to June: "2026/27". */
fun seasonOf(date: LocalDate): String {
    val start = if (date.month >= Month.JULY) date.year else date.year - 1
    return "$start/${((start + 1) % 100).toString().padStart(2, '0')}"
}
