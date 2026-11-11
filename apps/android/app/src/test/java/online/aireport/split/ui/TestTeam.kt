package online.aireport.split.ui

import java.time.LocalDate
import online.aireport.split.data.model.Balance
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Member
import online.aireport.split.data.model.MemberKind
import online.aireport.split.data.model.PotStatus
import online.aireport.split.data.model.Sport
import online.aireport.split.data.model.Transfer

/** A small team for unit tests: Anna, Bas (the signed-in member) and Cas, and their pot. */
internal object TestTeam {
    val today: LocalDate = LocalDate.of(2026, 11, 9)
    const val POT = "pot"
    val playerIds = listOf("anna", "bas", "cas")

    /** The ids in member order: the players, then the pot. */
    val memberIds = playerIds + POT

    val group = GroupDetail(
        id = "g1",
        name = "Heren 3",
        color = GroupColor.AMBER,
        sport = Sport.VOETBAL,
        club = "VV De Meern",
        season = "2026/27",
        inviteCode = "ABCD2345",
        myMemberId = "bas",
        potMemberId = POT,
        treasurerMemberId = "anna",
        members = playerIds.map { Member(it, it.replaceFirstChar(Char::uppercase)) } +
            Member(POT, "Teamkas", kind = MemberKind.POT),
        balances = listOf(
            Balance("anna", balanceCents = 4000, potCents = 5000, peerCents = -1000),
            Balance("bas", balanceCents = 800, potCents = -1200, peerCents = 2000),
            Balance("cas", balanceCents = -1000, potCents = 0, peerCents = -1000),
            Balance(POT, balanceCents = -3800, potCents = -3800, peerCents = 0),
        ),
        transfers = listOf(
            Transfer(POT, "anna", 5000),
            Transfer("bas", POT, 1200),
            Transfer("anna", "bas", 1000),
            Transfer("cas", "bas", 1000),
        ),
        pot = PotStatus(cashCents = 3500, toReceiveCents = 1200, toPayCents = 5000),
        fines = emptyList(),
        fineStats = emptyList(),
        totalSpentCents = 8500,
    )
}
