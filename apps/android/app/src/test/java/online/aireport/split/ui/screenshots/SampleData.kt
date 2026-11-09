package online.aireport.split.ui.screenshots

import java.time.LocalDate
import online.aireport.split.core.SplitMode
import online.aireport.split.data.model.Balance
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.ExpenseShare
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.SplitModeDto
import online.aireport.split.data.model.Transfer
import online.aireport.split.data.model.User
import online.aireport.split.ui.expense.ExpenseDraft
import online.aireport.split.ui.expense.ItemDraft
import online.aireport.split.ui.expense.newExpenseDraft

/** The household of the reference designs in `docs/design/v2`, as fake state for the screenshots. */
internal object SampleData {
    val today: LocalDate = LocalDate.of(2026, 11, 9)

    val user = User(id = "u1", name = "Sanne", email = "sanne@example.nl", iban = "NL91ABNA0417164300")

    private val sanne = Member("m1", "Sanne", userId = "u1", iban = "NL91ABNA0417164300")
    private val daan = Member("m2", "Daan", userId = "u2")
    private val yusuf = Member("m3", "Yusuf", userId = "u3", iban = "NL02RABO0123456789")
    private val lotte = Member("m4", "Lotte", userId = null)

    val group = GroupDetail(
        id = "g1",
        name = "Huis Vondelstraat",
        color = GroupColor.AMBER,
        inviteCode = "K7WQ2MXD",
        myMemberId = sanne.id,
        members = listOf(sanne, daan, yusuf, lotte),
        balances = listOf(
            Balance(sanne.id, 2495),
            Balance(daan.id, -2160),
            Balance(yusuf.id, 8000),
            Balance(lotte.id, -8335),
        ),
        transfers = listOf(
            Transfer(lotte.id, yusuf.id, 8000),
            Transfer(daan.id, sanne.id, 2160),
            Transfer(lotte.id, sanne.id, 335),
        ),
        totalSpentCents = 38716,
    )

    val groups = listOf(
        GroupSummary("g1", "Huis Vondelstraat", GroupColor.AMBER, memberCount = 4, myMemberId = "m1", myBalanceCents = 2495),
        GroupSummary("g2", "Weekend Texel", GroupColor.BLUE, memberCount = 2, myMemberId = "m9", myBalanceCents = 8460),
        GroupSummary("g3", "Etentje Yusuf", GroupColor.ROSE, memberCount = 5, myMemberId = "m12", myBalanceCents = 0),
    )

    val expenses = listOf(
        expense("e1", "Boodschappen Jumbo", 6412, sanne, "2026-11-07"),
        expense("e2", "Café De Zon", 8400, yusuf, "2026-11-05"),
        Expense(
            id = "e3",
            kind = ExpenseKind.SETTLEMENT,
            description = "Betaling",
            amountCents = 2500,
            paidById = daan.id,
            date = "2026-11-02",
            splitMode = SplitModeDto.EXACT,
            shares = listOf(ExpenseShare(sanne.id, 2500)),
        ),
        expense("e4", "Energie oktober", 18600, daan, "2026-10-28"),
        expense("e5", "Internet en tv", 5250, sanne, "2026-10-01"),
    )

    /** "Pizza bestellen", € 48,50, split equally: the expense form of the reference. */
    val equalDraft: ExpenseDraft = newExpenseDraft(group, today).copy(description = "Pizza bestellen", amountText = "48,50")

    /** The receipt of Café De Zon, split per line. */
    val itemsDraft: ExpenseDraft = newExpenseDraft(group, today).copy(
        description = "Café De Zon",
        amountText = "50,00",
        paidById = yusuf.id,
        mode = SplitMode.ITEMS,
        items = listOf(
            ItemDraft(1, "2x Bier", "9,00", setOf(sanne.id)),
            ItemDraft(2, "Witte wijn", "6,50", setOf(daan.id)),
            ItemDraft(3, "Bitterballen", "12,50", setOf(sanne.id, yusuf.id)),
            ItemDraft(4, "Burger met friet", "18,50", setOf(lotte.id)),
        ),
    )

    private fun expense(id: String, description: String, amountCents: Long, paidBy: Member, date: String): Expense {
        val share = amountCents / 4
        return Expense(
            id = id,
            kind = ExpenseKind.EXPENSE,
            description = description,
            amountCents = amountCents,
            paidById = paidBy.id,
            date = date,
            splitMode = SplitModeDto.EQUAL,
            shares = group.members.map { ExpenseShare(it.id, share) },
        )
    }
}
