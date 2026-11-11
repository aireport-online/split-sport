package online.aireport.split.ui.charge

import online.aireport.split.core.MAX_AMOUNT_CENTS
import online.aireport.split.core.formatAmountInput
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.Fine
import online.aireport.split.ui.TestTeam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeFormTest {
    private val group = TestTeam.group
    private val tooLate = Fine("f1", "Te laat", 200)

    @Test
    fun `a contribution starts with every team member ticked, never the pot`() {
        val form = newChargeForm(ChargeType.CONTRIBUTION, group, TestTeam.today)
        assertEquals(setOf("anna", "bas", "cas"), form.memberIds)
        assertFalse(group.potMemberId in form.memberIds)
    }

    @Test
    fun `a fine starts with nobody`() {
        assertTrue(newChargeForm(ChargeType.FINE, group, TestTeam.today).memberIds.isEmpty())
    }

    @Test
    fun `the total is the amount per person times who is charged`() {
        val form = newChargeForm(ChargeType.CONTRIBUTION, group, TestTeam.today)
            .copy(description = "Inleg november", amountText = "25,00")
        assertEquals(2500L, form.amountCents)
        assertEquals(7500L, form.totalCents)
        assertTrue(form.canSave)

        val withoutBas = form.toggleMember("bas")
        assertEquals(5000L, withoutBas.totalCents)
        assertEquals(7500L, withoutBas.toggleMember("bas").totalCents)
    }

    @Test
    fun `a charge needs a description, a positive amount and someone to charge`() {
        val complete = newChargeForm(ChargeType.FINE, group, TestTeam.today).withFine(tooLate).toggleMember("bas")
        assertTrue(complete.canSave)

        assertFalse(complete.copy(description = "  ").canSave)
        assertFalse(complete.copy(amountText = "").canSave)
        assertFalse(complete.copy(amountText = "0").canSave)
        assertFalse(complete.copy(amountText = "1,2,3").canSave)
        assertFalse(complete.toggleMember("bas").canSave)
        assertNull(complete.toggleMember("bas").totalCents)
    }

    @Test
    fun `the total may not be more than an entry can hold`() {
        val form = newChargeForm(ChargeType.CONTRIBUTION, group, TestTeam.today)
            .copy(description = "Te veel", amountText = formatAmountInput(MAX_AMOUNT_CENTS / 2))
        assertFalse(form.canSave)
        assertTrue(form.toggleMember("bas").canSave)
    }

    @Test
    fun `picking a line of the fine list fills in its name and amount`() {
        val form = newChargeForm(ChargeType.FINE, group, TestTeam.today).withFine(tooLate)
        assertEquals("Te laat", form.description)
        assertEquals("2,00", form.amountText)
        assertTrue(form.isFine(tooLate))

        // Typing over it makes it an own fine again.
        assertFalse(form.copy(amountText = "3").isFine(tooLate))
        assertFalse(form.copy(description = "Veel te laat").isFine(tooLate))
        assertTrue(form.copy(amountText = "2").isFine(tooLate))
    }
}
