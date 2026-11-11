package online.aireport.split.ui.screenshots

import java.time.LocalDate
import online.aireport.split.core.SplitMode
import online.aireport.split.data.model.Balance
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.ExpenseShare
import online.aireport.split.data.model.Fine
import online.aireport.split.data.model.FineStat
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.MemberKind
import online.aireport.split.data.model.PotStatus
import online.aireport.split.data.model.SplitModeDto
import online.aireport.split.data.model.Sport
import online.aireport.split.data.model.Transfer
import online.aireport.split.data.model.User
import online.aireport.split.ui.charge.ChargeForm
import online.aireport.split.ui.charge.newChargeForm
import online.aireport.split.ui.expense.ExpenseDraft
import online.aireport.split.ui.expense.ItemDraft
import online.aireport.split.ui.expense.newExpenseDraft

/**
 * The hockey team of the demo data (`apps/api/scripts/seed.ts`) as fake state for the
 * screenshots, with the balances the server would compute for it.
 */
internal object SampleData {
    val today: LocalDate = LocalDate.of(2026, 11, 9)

    val user = User(id = "u1", name = "Sanne", email = "sanne@example.nl", iban = "NL91ABNA0417164300")

    private val sanne = Member("m1", "Sanne", userId = "u1", iban = "NL91ABNA0417164300")
    private val fleur = Member("m2", "Fleur", userId = "u2", iban = "NL02RABO0123456789")
    private val noor = Member("m3", "Noor", userId = "u3")
    private val lotte = Member("m4", "Lotte")
    private val emma = Member("m5", "Emma")
    private val pot = Member("pot", "Teamkas", kind = MemberKind.POT)
    private val players = listOf(sanne, fleur, noor, lotte, emma)

    val group = GroupDetail(
        id = "g1",
        name = "Dames 2",
        color = GroupColor.ROSE,
        sport = Sport.HOCKEY,
        club = "MHC Oostpoort",
        season = "2026/27",
        inviteCode = "K7WQ2MXD",
        myMemberId = sanne.id,
        potMemberId = pot.id,
        treasurerMemberId = sanne.id,
        members = players + pot,
        balances = listOf(
            Balance(sanne.id, balanceCents = 595, potCents = 0, peerCents = 595),
            Balance(fleur.id, balanceCents = 2547, potCents = 4550, peerCents = -2003),
            Balance(noor.id, balanceCents = 2617, potCents = -900, peerCents = 3517),
            Balance(lotte.id, balanceCents = -1256, potCents = 0, peerCents = -1256),
            Balance(emma.id, balanceCents = -3853, potCents = -3000, peerCents = -853),
            Balance(pot.id, balanceCents = -650, potCents = -650, peerCents = 0),
        ),
        transfers = listOf(
            Transfer(pot.id, fleur.id, 4550),
            Transfer(noor.id, pot.id, 900),
            Transfer(emma.id, pot.id, 3000),
            Transfer(fleur.id, noor.id, 2003),
            Transfer(lotte.id, noor.id, 1256),
            Transfer(emma.id, sanne.id, 595),
            Transfer(emma.id, noor.id, 258),
        ),
        pot = PotStatus(cashCents = 4750, toReceiveCents = 3900, toPayCents = 4550),
        fines = listOf(
            Fine("f1", "Te laat", 200),
            Fine("f2", "Niet afgemeld", 500),
            Fine("f3", "Spullen vergeten", 200),
            Fine("f4", "Gele kaart", 500),
            Fine("f5", "Rode kaart", 1000),
        ),
        fineStats = listOf(FineStat(noor.id, 3, 900), FineStat(emma.id, 1, 500), FineStat(fleur.id, 1, 200)),
        totalSpentCents = 17532,
    )

    val groups = listOf(
        GroupSummary("g1", "Dames 2", GroupColor.ROSE, Sport.HOCKEY, "MHC Oostpoort", "2026/27", 5, "m1", 595),
        GroupSummary("g2", "Zaalvoetbal vrijdag", GroupColor.GREEN, Sport.VOETBAL, "SV Oostpoort", "2026/27", 2, "m9", -1800),
        GroupSummary("g3", "Padel op zondag", GroupColor.BLUE, Sport.PADEL, club = null, season = null, 4, "m12", 0),
    )

    /** Newest first, one of every kind of entry. */
    val expenses = listOf(
        entry("e1", ExpenseKind.EXPENSE, "Bloemen voor de trainer", 2250, pot, "2026-11-07", pot to 2250),
        entry("e2", ExpenseKind.FINE, "Te laat", 200, pot, "2026-11-06", noor to 200),
        entry(
            "e3", ExpenseKind.EXPENSE, "Derde helft clubhuis", 5600, noor, "2026-11-02",
            sanne to 854, noor to 1600, emma to 853, fleur to 1520, lotte to 773,
        ),
        entry("e4", ExpenseKind.EXPENSE, "Wedstrijdballen", 4750, fleur, "2026-10-14", pot to 4750),
        entry("e5", ExpenseKind.SETTLEMENT, "Betaling", 2500, lotte, "2026-10-10", pot to 2500),
        entry("e6", ExpenseKind.CONTRIBUTION, "Inleg eerste seizoenshelft", 12500, pot, "2026-09-28", *players.map { it to 2500L }.toTypedArray()),
    )

    /** "Rit (84 km)", € 19,32, split equally over the players; the Teamkas is listed but unticked. */
    val equalDraft: ExpenseDraft = newExpenseDraft(group, today).copy(description = "Rit (84 km)", amountText = "19,32")

    /** The receipt of the third half in the club house, split per line. */
    val itemsDraft: ExpenseDraft = newExpenseDraft(group, today).copy(
        description = "Derde helft clubhuis",
        amountText = "56,00",
        paidById = noor.id,
        mode = SplitMode.ITEMS,
        items = listOf(
            ItemDraft(1, "Kan bier", "16,50", setOf(sanne.id, noor.id, emma.id)),
            ItemDraft(2, "2× Witte wijn", "9,50", setOf(fleur.id, lotte.id)),
            ItemDraft(3, "Bitterballen", "12,50", players.map { it.id }.toSet()),
            ItemDraft(4, "Tosti's", "14,00", setOf(fleur.id, noor.id)),
        ),
    )

    /** "Te laat" for Noor, picked from the fine list. */
    val fineForm: ChargeForm = newChargeForm(ChargeType.FINE, group, today)
        .toggleMember(noor.id)
        .withFine(group.fines.first())

    /** "Inleg november", € 25,00 each, everyone ticked. */
    val contributionForm: ChargeForm =
        newChargeForm(ChargeType.CONTRIBUTION, group, today).copy(description = "Inleg november", amountText = "25,00")

    private fun entry(
        id: String,
        kind: ExpenseKind,
        description: String,
        amountCents: Long,
        paidBy: Member,
        date: String,
        vararg shares: Pair<Member, Long>,
    ) = Expense(
        id = id,
        kind = kind,
        description = description,
        amountCents = amountCents,
        paidById = paidBy.id,
        date = date,
        splitMode = SplitModeDto.EXACT,
        shares = shares.map { (member, cents) -> ExpenseShare(member.id, cents) },
    )
}
