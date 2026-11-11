package online.aireport.split.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamHelpersTest {
    @Test
    fun `a season runs from July to June`() {
        assertEquals("2026/27", seasonOf(LocalDate.of(2026, 11, 9)))
        assertEquals("2026/27", seasonOf(LocalDate.of(2026, 7, 1)))
        assertEquals("2025/26", seasonOf(LocalDate.of(2026, 6, 30)))
        assertEquals("2026/27", seasonOf(LocalDate.of(2027, 1, 15)))
        assertEquals("2099/00", seasonOf(LocalDate.of(2099, 9, 1)))
        assertEquals("2008/09", seasonOf(LocalDate.of(2009, 2, 1)))
    }

    @Test
    fun `a ride costs its kilometres times the rate`() {
        assertEquals(1932L, tripCostCents("84", DEFAULT_RATE_CENTS_PER_KM))
        assertEquals(288L, tripCostCents("12,5", 23))
        assertEquals(288L, tripCostCents(" 12.5 ", 23))
        assertEquals(2100L, tripCostCents("84", 25))
    }

    @Test
    fun `half cents are rounded up`() {
        // 0,5 km at 23 cents is 11,5 cents.
        assertEquals(12L, tripCostCents("0,5", 23))
        assertEquals(2L, tripCostCents("0,1", 23))
    }

    @Test
    fun `a ride needs a positive distance and a cost an expense can have`() {
        assertNull(tripCostCents("", 23))
        assertNull(tripCostCents("0", 23))
        assertNull(tripCostCents("abc", 23))
        assertNull(tripCostCents("-5", 23))
        assertNull(tripCostCents("1,25", 23))
        assertNull(tripCostCents("84", 0))
        assertNull(tripCostCents("99999", 100_000))
    }

    @Test
    fun `kilometres are written without needless decimals`() {
        assertEquals("84", formatKilometres("84"))
        assertEquals("84", formatKilometres("84,0"))
        assertEquals("12,5", formatKilometres("12.5"))
        assertEquals("100", formatKilometres("100"))
        assertNull(formatKilometres("veel"))
    }
}
