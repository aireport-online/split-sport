package online.aireport.split.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats cents as Dutch euros`() {
        assertEquals("€ 0,00", formatCents(0))
        assertEquals("€ 0,05", formatCents(5))
        assertEquals("€ 12,50", formatCents(1250))
        assertEquals("€ 1.234,56", formatCents(123_456))
        assertEquals("€ 999.999,99", formatCents(99_999_999))
        assertEquals("€ 1.000.000,00", formatCents(100_000_000))
        assertEquals("€ -8,00", formatCents(-800))
    }

    @Test
    fun `formats amounts as a till prints them`() {
        assertEquals("64,12", formatAmount(6412))
        assertEquals("1.234,56", formatAmount(123_456))
        assertEquals("8,00", formatAmount(-800))
    }

    @Test
    fun `a balance always has its sign`() {
        assertEquals("+\u00a024,95", formatSignedAmount(2495))
        assertEquals("−\u00a021,60", formatSignedAmount(-2160))
        assertEquals("0,00", formatSignedAmount(0))
        assertEquals("+\u00a0€\u00a0109,55", formatSignedCents(10_955))
        assertEquals("−\u00a0€\u00a08,00", formatSignedCents(-800))
        assertEquals("€\u00a00,00", formatSignedCents(0))
    }

    @Test
    fun `formats cents for an input field`() {
        assertEquals("12,50", formatAmountInput(1250))
        assertEquals("0,05", formatAmountInput(5))
        assertEquals("1234,00", formatAmountInput(123_400))
    }

    @Test
    fun `parses comma and dot decimals`() {
        assertEquals(1250L, parseAmount("12,50"))
        assertEquals(1250L, parseAmount("12.5"))
        assertEquals(1200L, parseAmount("12"))
        assertEquals(1200L, parseAmount("12,"))
        assertEquals(1200L, parseAmount("€ 12"))
        assertEquals(5L, parseAmount("0,05"))
        assertEquals(0L, parseAmount("0"))
    }

    @Test
    fun `tells thousands separators from decimal ones`() {
        assertEquals(123_456L, parseAmount("1.234,56"))
        assertEquals(123_456L, parseAmount("1,234.56"))
        assertEquals(123_400L, parseAmount("1.234"))
        assertEquals(123_456_700L, parseAmount("1,234,567"))
    }

    @Test
    fun `rejects what is not an amount`() {
        assertNull(parseAmount(""))
        assertNull(parseAmount("   "))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("-5"))
        assertNull(parseAmount("12,345,6"))
        assertNull(parseAmount("1.2.3"))
        assertNull(parseAmount(",5"))
        assertNull(parseAmount("99999999999999999999"))
    }

    @Test
    fun `what is formatted for a field parses back`() {
        for (cents in listOf(0L, 1L, 99L, 100L, 1250L, 123_456L, 99_999_999L)) {
            assertEquals(cents, parseAmount(formatAmountInput(cents)))
            assertEquals(cents, parseAmount(formatCents(cents)))
        }
    }
}
