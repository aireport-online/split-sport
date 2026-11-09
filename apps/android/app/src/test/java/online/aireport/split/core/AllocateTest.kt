package online.aireport.split.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AllocateTest {
    @Test
    fun `leftover cents go to the largest remainders, ties to the earlier index`() {
        assertEquals(listOf(34L, 33L, 33L), allocate(100, listOf(1, 1, 1)))
        assertEquals(listOf(25L, 25L, 51L), allocate(101, listOf(1, 1, 2)))
    }

    @Test
    fun `a negative total is the negated allocation of its absolute value`() {
        assertEquals(listOf(-34L, -33L, -33L), allocate(-100, listOf(1, 1, 1)))
        assertEquals(listOf(0L, -1L), allocate(-1, listOf(1, 2)))
    }

    @Test
    fun `zero is spread as zeroes`() {
        assertEquals(listOf(0L, 0L), allocate(0, listOf(3, 5)))
    }

    @Test
    fun `the largest amount times the largest weight does not overflow`() {
        val parts = allocate(MAX_AMOUNT_CENTS, listOf(MAX_WEIGHT.toLong(), MAX_WEIGHT.toLong(), 1))
        assertEquals(MAX_AMOUNT_CENTS, parts.sum())
        assertEquals(listOf(49_999_975L, 49_999_974L, 50L), parts)
    }

    @Test
    fun `needs at least one positive weight`() {
        assertThrows(SplitException::class.java) { allocate(100, emptyList()) }
        assertThrows(SplitException::class.java) { allocate(100, listOf(0, 0)) }
    }
}
