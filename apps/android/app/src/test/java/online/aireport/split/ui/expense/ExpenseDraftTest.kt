package online.aireport.split.ui.expense

import java.time.LocalDate
import online.aireport.split.core.Share
import online.aireport.split.core.Split
import online.aireport.split.core.SplitErrorCode
import online.aireport.split.core.SplitMode
import online.aireport.split.data.api.SplitJson
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseItem
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.ExpenseShare
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.ReceiptLine
import online.aireport.split.data.model.ReceiptScan
import online.aireport.split.data.model.SplitModeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseDraftTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val memberIds = listOf("anna", "bas", "cas")
    private val group = GroupDetail(
        id = "g1",
        name = "Huis",
        color = GroupColor.AMBER,
        inviteCode = "ABCD2345",
        myMemberId = "bas",
        members = memberIds.map { Member(it, it.replaceFirstChar(Char::uppercase)) },
        balances = emptyList(),
        transfers = emptyList(),
        totalSpentCents = 0,
    )
    private val fresh = newExpenseDraft(group, today)

    @Test
    fun `a new expense is paid by you today and split equally over everyone`() {
        assertEquals("bas", fresh.paidById)
        assertEquals(today, fresh.date)
        assertEquals(SplitMode.EQUAL, fresh.mode)

        val preview = fresh.copy(amountText = "10").preview(memberIds) as SplitPreview.Valid

        assertEquals(listOf(Share("anna", 334), Share("bas", 333), Share("cas", 333)), preview.shares)
        assertEquals(Split.Equal(memberIds), preview.split)
    }

    @Test
    fun `without an amount there is nothing to preview`() {
        assertEquals(SplitPreview.Invalid(SplitErrorCode.INVALID_AMOUNT), fresh.preview(memberIds))
        assertEquals(SplitPreview.Invalid(SplitErrorCode.INVALID_AMOUNT), fresh.copy(amountText = "abc").preview(memberIds))
    }

    @Test
    fun `equal - unticking everyone leaves no participants`() {
        val draft = memberIds.fold(fresh.copy(amountText = "10")) { draft, id -> draft.toggleEqualMember(id) }
        assertEquals(SplitPreview.Invalid(SplitErrorCode.NO_PARTICIPANTS), draft.preview(memberIds))
    }

    @Test
    fun `equal - the split follows the group's member order, not the order of ticking`() {
        val draft = fresh.copy(amountText = "10").toggleEqualMember("anna").toggleEqualMember("anna")
        assertEquals(Split.Equal(memberIds), (draft.preview(memberIds) as SplitPreview.Valid).split)
    }

    @Test
    fun `exact - shows what is still to divide until the amounts add up`() {
        val draft = fresh.copy(amountText = "10,00", mode = SplitMode.EXACT).setExactAmount("anna", "7,50")
        assertEquals(250L, draft.exactRemainingCents)
        assertEquals(SplitPreview.Invalid(SplitErrorCode.EXACT_SUM_MISMATCH), draft.preview(memberIds))

        val complete = draft.setExactAmount("cas", "2.5")
        assertEquals(0L, complete.exactRemainingCents)
        assertEquals(
            listOf(Share("anna", 750), Share("cas", 250)),
            (complete.preview(memberIds) as SplitPreview.Valid).shares,
        )

        assertEquals(-100L, complete.setExactAmount("bas", "1").exactRemainingCents)
    }

    @Test
    fun `exact - an unreadable amount is not silently counted as zero`() {
        val draft = fresh.copy(amountText = "10", mode = SplitMode.EXACT)
            .setExactAmount("anna", "10")
            .setExactAmount("bas", "1,2,3")
        assertEquals(SplitPreview.Invalid(SplitErrorCode.INVALID_AMOUNT), draft.preview(memberIds))
    }

    @Test
    fun `shares - a stepper never goes below zero and zero means not in`() {
        val draft = fresh.copy(amountText = "9", mode = SplitMode.SHARES)
            .changeWeight("anna", 1)
            .changeWeight("cas", -1)
            .changeWeight("cas", -1)
        assertEquals(mapOf("anna" to 2, "bas" to 1, "cas" to 0), draft.weights)
        assertEquals(
            listOf(Share("anna", 600), Share("bas", 300)),
            (draft.preview(memberIds) as SplitPreview.Valid).shares,
        )
    }

    @Test
    fun `items - an empty amount field means the total of the lines`() {
        val draft = fresh.copy(mode = SplitMode.ITEMS)
            .addItem(listOf("anna"))
            .addItem(listOf("anna", "bas"))
            .updateItem(1) { it.copy(name = "Pizza", amountText = "11,00") }
            .updateItem(2) { it.copy(name = "Wijn", amountText = "10,01") }

        assertEquals(2101L, draft.amountCents)
        assertEquals(0L, draft.itemsDifferenceCents)
        assertEquals(
            listOf(Share("anna", 1601), Share("bas", 500)),
            (draft.preview(memberIds) as SplitPreview.Valid).shares,
        )
    }

    @Test
    fun `items - a difference with the total is spread in proportion`() {
        val draft = fresh.copy(mode = SplitMode.ITEMS, amountText = "44,00")
            .addItem(listOf("anna"))
            .addItem(listOf("bas"))
            .updateItem(1) { it.copy(amountText = "30") }
            .updateItem(2) { it.copy(amountText = "10") }

        assertEquals(400L, draft.itemsDifferenceCents)
        assertEquals(
            listOf(Share("anna", 3300), Share("bas", 1100)),
            (draft.preview(memberIds) as SplitPreview.Valid).shares,
        )
    }

    @Test
    fun `items - a line needs an amount and someone to pay for it`() {
        val withoutAmount = fresh.copy(mode = SplitMode.ITEMS, amountText = "10").addItem(memberIds)
        assertEquals(SplitPreview.Invalid(SplitErrorCode.INVALID_ITEM), withoutAmount.preview(memberIds))

        val unassigned = withoutAmount
            .updateItem(1) { it.copy(amountText = "10") }
            .let { draft -> memberIds.fold(draft) { d, id -> d.toggleItemMember(1, id) } }
        assertEquals(SplitPreview.Invalid(SplitErrorCode.ITEM_UNASSIGNED), unassigned.preview(memberIds))
    }

    @Test
    fun `items - removed lines do not hand their key to new ones`() {
        val draft = fresh.addItem(memberIds).addItem(memberIds).addItem(memberIds).removeItem(2).addItem(memberIds)
        assertEquals(listOf(1L, 3L, 4L), draft.items.map { it.key })
    }

    @Test
    fun `an existing expense is rebuilt from what the server stored`() {
        val stored = Expense(
            id = "e1",
            kind = ExpenseKind.EXPENSE,
            description = "Boodschappen",
            amountCents = 900,
            paidById = "anna",
            date = "2026-10-31",
            splitMode = SplitModeDto.SHARES,
            shares = listOf(ExpenseShare("anna", 600, weight = 2), ExpenseShare("cas", 300, weight = 1)),
        )

        val draft = expenseDraft(stored, group, today)

        assertEquals("9,00", draft.amountText)
        assertEquals(LocalDate.of(2026, 10, 31), draft.date)
        assertEquals("anna", draft.paidById)
        assertEquals(mapOf("anna" to 2, "cas" to 1), draft.weights)
        assertEquals(
            listOf(Share("anna", 600), Share("cas", 300)),
            (draft.preview(memberIds) as SplitPreview.Valid).shares,
        )
    }

    @Test
    fun `an existing receipt keeps its lines and who had them`() {
        val stored = Expense(
            id = "e2",
            kind = ExpenseKind.EXPENSE,
            description = "Café",
            amountCents = 1500,
            paidById = "bas",
            date = "2026-11-01",
            splitMode = SplitModeDto.ITEMS,
            shares = listOf(ExpenseShare("anna", 1000), ExpenseShare("bas", 500)),
            items = listOf(ExpenseItem("Wijn", 1000, listOf("anna")), ExpenseItem("Koffie", 500, listOf("bas"))),
        )

        val draft = expenseDraft(stored, group, today)

        assertEquals(listOf("Wijn" to "10,00", "Koffie" to "5,00"), draft.items.map { it.name to it.amountText })
        assertEquals(listOf(setOf("anna"), setOf("bas")), draft.items.map { it.memberIds })
    }

    @Test
    fun `a scanned receipt fills the lines, the total, the date and an empty description`() {
        val scan = ReceiptScan(
            merchant = "Café De Zon",
            date = "2026-11-05",
            totalCents = 2350,
            items = listOf(ReceiptLine("Bier", 900), ReceiptLine("Korting", -150), ReceiptLine("Burger", 1600)),
        )

        val draft = fresh.withReceipt(scan, memberIds)

        assertEquals(SplitMode.ITEMS, draft.mode)
        assertEquals("Café De Zon", draft.description)
        assertEquals("23,50", draft.amountText)
        assertEquals(LocalDate.of(2026, 11, 5), draft.date)
        // The discount line cannot be a line of the split; it shows up as the difference.
        assertEquals(listOf("Bier", "Burger"), draft.items.map { it.name })
        assertEquals(-150L, draft.itemsDifferenceCents)
        assertEquals(memberIds.toSet(), draft.items.first().memberIds)
    }

    @Test
    fun `a scan keeps what it could not read and a description that was typed`() {
        val typed = fresh.copy(description = "Verjaardag", amountText = "40")
        val scan = ReceiptScan(merchant = "Jumbo", date = null, totalCents = null, items = emptyList())

        val draft = typed.withReceipt(scan, memberIds)

        assertEquals("Verjaardag", draft.description)
        assertEquals("40", draft.amountText)
        assertEquals(today, draft.date)
        assertNull(draft.items.firstOrNull())
    }

    @Test
    fun `a draft survives being saved and restored`() {
        val draft = fresh.copy(description = "Café", amountText = "12,50", mode = SplitMode.ITEMS)
            .addItem(listOf("anna", "cas"))
            .setExactAmount("bas", "1")

        val restored = SplitJson.decodeFromString<ExpenseDraft>(SplitJson.encodeToString(draft))

        assertEquals(draft, restored)
    }
}
