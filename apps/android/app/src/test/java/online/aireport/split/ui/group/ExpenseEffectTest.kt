package online.aireport.split.ui.group

import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.ExpenseShare
import online.aireport.split.data.model.SplitModeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseEffectTest {
    private val dinner = Expense(
        id = "e1",
        kind = ExpenseKind.EXPENSE,
        description = "Etentje",
        amountCents = 1000,
        paidById = "anna",
        date = "2026-11-02",
        splitMode = SplitModeDto.EQUAL,
        shares = listOf(ExpenseShare("anna", 334), ExpenseShare("bas", 333), ExpenseShare("cas", 333)),
    )

    @Test
    fun `who paid gets back what the others owe`() {
        assertEquals(666L, dinner.effectFor("anna"))
    }

    @Test
    fun `who took part owes their share`() {
        assertEquals(-333L, dinner.effectFor("bas"))
    }

    @Test
    fun `who is not in it sees nothing`() {
        assertNull(dinner.effectFor("dirk"))
    }

    @Test
    fun `paying only for yourself evens out`() {
        val solo = dinner.copy(shares = listOf(ExpenseShare("anna", 1000)))
        assertNull(solo.effectFor("anna"))
    }

    @Test
    fun `paying for others without taking part is all to get back`() {
        val treat = dinner.copy(shares = listOf(ExpenseShare("bas", 500), ExpenseShare("cas", 500)))
        assertEquals(1000L, treat.effectFor("anna"))
    }

    @Test
    fun `what the pot pays for itself or a member advances for the pot is not yours`() {
        val referee = dinner.copy(paidById = "pot", shares = listOf(ExpenseShare("pot", 1000)))
        assertNull(referee.effectFor("anna"))
        val balls = dinner.copy(paidById = "bas", shares = listOf(ExpenseShare("pot", 1000)))
        assertNull(balls.effectFor("anna"))
        assertEquals(1000L, balls.effectFor("bas"))
    }

    @Test
    fun `fines and contributions have a stamp, no effect line`() {
        val fine = dinner.copy(kind = ExpenseKind.FINE, paidById = "pot", shares = listOf(ExpenseShare("anna", 200)))
        assertNull(fine.effectFor("anna"))
        assertNull(fine.copy(kind = ExpenseKind.CONTRIBUTION).effectFor("anna"))
    }

    @Test
    fun `a settlement has no effect line`() {
        val settlement = dinner.copy(kind = ExpenseKind.SETTLEMENT, shares = listOf(ExpenseShare("bas", 1000)))
        assertNull(settlement.effectFor("anna"))
        assertNull(settlement.effectFor("bas"))
    }
}
