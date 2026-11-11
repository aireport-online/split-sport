package online.aireport.split.data

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import online.aireport.split.core.Share
import online.aireport.split.core.Split
import online.aireport.split.core.SplitItem
import online.aireport.split.core.Weight
import online.aireport.split.core.computeShares
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.FineStat
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.PotStatus
import online.aireport.split.data.model.Sport
import online.aireport.split.data.model.Transfer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs the data layer against a live Split Sport server. Skipped unless `SPLIT_API_URL` is set:
 *
 *     SPLIT_API_URL=http://localhost:8791 ./gradlew :app:testDebugUnitTest --tests '*IntegrationTest'
 */
class SplitApiIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val serverUrl = System.getenv("SPLIT_API_URL").orEmpty()
    private val today = LocalDate.now()
    private val backends = mutableListOf<TestBackend>()

    @Before
    fun requireServer() {
        assumeTrue("SPLIT_API_URL is not set", serverUrl.isNotBlank())
    }

    @After
    fun tearDown() = backends.forEach(TestBackend::close)

    @Test
    fun `a team from registration to a settled balance and a deleted account`() = runBlocking {
        val anna = newDevice()
        val email = "anna-${UUID.randomUUID()}@example.org"

        // Account
        anna.accounts.register(serverUrl, "Anna", email, "geheim-wachtwoord")
        assertTrue(anna.sessionStore.current().isSignedIn)
        assertEquals("Anna", anna.accounts.profile().name)
        assertEquals("NL91ABNA0417164300", anna.accounts.updateProfile("Anna", "nl91 abna 0417 1643 00").iban)
        assertTrue(anna.groups.groups().isEmpty())

        // A team has a pot, a penningmeester (whoever made it) and a fine list from the start.
        var group = anna.groups.createGroup(NewTeam("Heren 3", GroupColor.TEAL, Sport.VOETBAL, club = "VV De Meern", season = "2026/27"))
        assertEquals(Sport.VOETBAL, group.sport)
        assertEquals("VV De Meern", group.club)
        assertEquals("2026/27", group.season)
        assertEquals(group.myMemberId, group.treasurerMemberId)
        assertEquals("NL91ABNA0417164300", group.treasurer?.iban)
        assertEquals(group.potMemberId, group.members.last().id)
        assertTrue(group.members.last().isPot)
        assertEquals("Teamkas", group.members.last().name)
        assertTrue(group.fines.isNotEmpty())
        assertEquals(PotStatus(cashCents = 0, toReceiveCents = 0, toPayCents = 0), group.pot)

        // Team members without an account; the pot stays last and is not one of the players.
        group = anna.groups.addMember(group.id, "Bas")
        group = anna.groups.addMember(group.id, "Cas")
        group = anna.groups.addMember(group.id, "Dirk")
        val (me, bas, cas, dirk) = group.players.map { it.id }
        val pot = group.potMemberId
        assertEquals(me, group.myMemberId)
        assertEquals(pot, group.members.last().id)
        group = anna.groups.renameMember(group.id, dirk, "Dirk-Jan")
        assertEquals("Dirk-Jan", group.member(dirk)?.name)
        group = anna.groups.removeMember(group.id, dirk)
        assertEquals(listOf("Anna", "Bas", "Cas"), group.players.map { it.name })
        assertEquals(3, anna.groups.groups().single().memberCount)

        // Team details: name, club (cleared), season, sport, highlighter.
        group = anna.groups.updateDetails(group.id, "Heren 4", club = "", season = "2027/28")
        assertEquals("Heren 4", group.name)
        assertNull(group.club)
        assertEquals("2027/28", group.season)
        assertEquals(Sport.HOCKEY, anna.groups.changeSport(group.id, Sport.HOCKEY).sport)
        assertEquals(GroupColor.ROSE, anna.groups.recolorGroup(group.id, GroupColor.ROSE).color)
        val summary = anna.groups.groups().single()
        assertEquals(Sport.HOCKEY, summary.sport)
        assertNull(summary.club)
        assertEquals("2027/28", summary.season)

        // The fine list: add, change, remove.
        group = anna.groups.addFine(group.id, "Eigen goal", 250)
        val ownGoal = group.fines.last()
        assertEquals("Eigen goal" to 250L, ownGoal.name to ownGoal.amountCents)
        group = anna.groups.updateFine(group.id, ownGoal.id, "Eigen doelpunt", 300)
        assertEquals("Eigen doelpunt" to 300L, group.fines.last().let { it.name to it.amountCents })
        group = anna.groups.removeFine(group.id, ownGoal.id)
        assertFalse(group.fines.any { it.id == ownGoal.id })
        assertEquals("validation_error", failureOf { anna.groups.addFine(group.id, "Gratis", 0) }.code)

        // ---- The team pot ----------------------------------------------------------------

        // Everyone is asked for € 25; Bas gets a fine. Claims, not cash.
        val contribution = anna.expenses.charge(group.id, ChargeType.CONTRIBUTION, "Inleg november", 2500, listOf(me, bas, cas), today)
        assertEquals(ExpenseKind.CONTRIBUTION, contribution.kind)
        assertEquals(7500L, contribution.amountCents)
        assertEquals(pot, contribution.paidById)
        assertEquals(listOf(me to 2500L, bas to 2500L, cas to 2500L), contribution.shares.map { it.memberId to it.amountCents })
        val fine = anna.expenses.charge(group.id, ChargeType.FINE, "Te laat", 200, listOf(bas), today)
        assertEquals(ExpenseKind.FINE, fine.kind)
        assertEquals(pot, fine.paidById)

        group = anna.groups.group(group.id)
        assertEquals(PotStatus(cashCents = 0, toReceiveCents = 7700, toPayCents = 0), group.pot)
        assertEquals(listOf(FineStat(bas, count = 1, totalCents = 200)), group.fineStats)
        assertEquals(-2700L, group.balance(bas).potCents)
        assertEquals(0L, group.balance(bas).peerCents)
        assertEquals(7700L, group.balance(pot).potCents)
        assertEquals(0L, group.totalSpentCents)
        assertEquals(
            listOf(Transfer(me, pot, 2500), Transfer(bas, pot, 2700), Transfer(cas, pot, 2500)),
            group.potTransfers,
        )
        assertTrue(group.peerTransfers.isEmpty())

        // The pot cannot be charged, and a charge cannot be edited, only removed.
        assertEquals(400, failureOf { anna.expenses.charge(group.id, ChargeType.FINE, "x", 100, listOf(pot), today) }.status)
        val editFine = failureOf {
            anna.expenses.updateExpense(group.id, fine.id, ExpenseInput("Te laat", 100, today, me, Split.Equal(listOf(me))))
        }
        assertEquals(404, editFine.status)

        // Anna and Bas pay the pot: now it is cash.
        anna.expenses.settle(group.id, me, pot, 2500, today)
        val paid = anna.expenses.settle(group.id, bas, pot, 2700, today)
        assertEquals(ExpenseKind.SETTLEMENT, paid.kind)
        assertEquals(pot, paid.shares.single().memberId)
        group = anna.groups.group(group.id)
        assertEquals(PotStatus(cashCents = 5200, toReceiveCents = 2500, toPayCents = 0), group.pot)

        // The pot pays the referee (nobody is charged); Cas buys balls for the team (the pot owes him).
        anna.expenses.createExpense(group.id, ExpenseInput("Scheidsrechter", 1500, today, paidById = pot, split = Split.Equal(listOf(pot))))
        anna.expenses.createExpense(group.id, ExpenseInput("Wedstrijdballen", 4000, today, paidById = cas, split = Split.Equal(listOf(pot))))
        group = anna.groups.group(group.id)
        assertEquals(PotStatus(cashCents = 3700, toReceiveCents = 0, toPayCents = 1500), group.pot)
        // Cas owed 25,00 and advanced 40,00: the pot owes him the difference.
        assertEquals(1500L, group.balance(cas).potCents)
        assertEquals(listOf(Transfer(pot, cas, 1500)), group.potTransfers)
        assertEquals(5500L, group.totalSpentCents)

        // The pot pays Cas back: nothing open with the pot any more.
        anna.expenses.settle(group.id, pot, cas, 1500, today)
        group = anna.groups.group(group.id)
        assertEquals(PotStatus(cashCents = 2200, toReceiveCents = 0, toPayCents = 0), group.pot)
        assertTrue(group.potTransfers.isEmpty())

        // Removing the fine takes Bas off the ranking, and the pot then owes him what he paid for it.
        anna.expenses.deleteExpense(group.id, fine.id)
        group = anna.groups.group(group.id)
        assertTrue(group.fineStats.isEmpty())
        assertEquals(listOf(Transfer(pot, bas, 200)), group.potTransfers)

        // The pot is handed to a team member, never to the pot itself.
        assertEquals(bas, anna.groups.changeTreasurer(group.id, bas).treasurerMemberId)
        assertEquals(400, failureOf { anna.groups.changeTreasurer(group.id, pot) }.status)
        assertEquals(me, anna.groups.changeTreasurer(group.id, me).treasurerMemberId)
        assertEquals(403, failureOf { anna.groups.renameMember(group.id, pot, "Pot") }.status)
        assertEquals(403, failureOf { anna.groups.removeMember(group.id, pot) }.status)

        // ---- Among team members -----------------------------------------------------------

        // One expense per split mode; the server must agree with the local computeShares.
        val splits = listOf(
            1000L to Split.Equal(listOf(me, bas, cas)),
            5000L to Split.Exact(listOf(Share(me, 1250), Share(bas, 0), Share(cas, 3750))),
            1000L to Split.Shares(listOf(Weight(me, 1), Weight(bas, 2), Weight(cas, 0), Weight(pot, 0))),
            2500L to Split.Items(
                listOf(
                    SplitItem("Burger", 1450, listOf(me)),
                    SplitItem("Friet", 450, listOf(me, bas, cas)),
                    SplitItem("Bier", 1100, listOf(bas, cas)),
                ),
            ),
        )
        val created = splits.map { (amountCents, split) ->
            val input = ExpenseInput("Uitgave ${split::class.simpleName}", amountCents, today, paidById = me, split = split)
            val expense = anna.expenses.createExpense(group.id, input)
            assertEquals(
                "Shares of the ${split::class.simpleName} split",
                computeShares(amountCents, split),
                expense.shares.map { Share(it.memberId, it.amountCents) },
            )
            expense
        }
        assertEquals(listOf(1, 2), created[2].shares.map { it.weight })
        assertEquals(3, created[3].items.size)
        assertEquals(created[3], anna.expenses.expense(group.id, created[3].id))
        val kinds = anna.expenses.expenses(group.id).groupingBy { it.kind }.eachCount()
        assertEquals(
            mapOf(ExpenseKind.EXPENSE to 6, ExpenseKind.SETTLEMENT to 3, ExpenseKind.CONTRIBUTION to 1),
            kinds,
        )

        // Edit: the equal split becomes 12,00 over two people.
        val edited = anna.expenses.updateExpense(
            group.id,
            created[0].id,
            ExpenseInput("Boodschappen", 1200, today.minusDays(1), paidById = bas, split = Split.Equal(listOf(me, bas))),
        )
        assertEquals("Boodschappen", edited.description)
        assertEquals(bas, edited.paidById)
        assertEquals(listOf(600L, 600L), edited.shares.map { it.amountCents })

        // The two sides add up, and what members owe each other is settled among members only.
        group = anna.groups.group(group.id)
        assertEquals(0L, group.balances.sumOf { it.balanceCents })
        assertEquals(0L, group.players.sumOf { group.balance(it.id).peerCents })
        assertTrue(group.balances.all { it.balanceCents == it.potCents + it.peerCents })
        val myPeer = group.balance(me).peerCents
        assertTrue("Anna's balance among members is $myPeer", myPeer > 0)
        assertEquals(group.balance(me).balanceCents, anna.groups.groups().single().myBalanceCents)
        assertTrue(group.peerTransfers.isNotEmpty())
        assertTrue(group.peerTransfers.none { it.fromMemberId == pot || it.toMemberId == pot })

        // Settle every suggested payment, with the pot and among members: everyone is square.
        for (transfer in group.transfers) {
            anna.expenses.settle(group.id, transfer.fromMemberId, transfer.toMemberId, transfer.amountCents, today)
        }
        group = anna.groups.group(group.id)
        assertTrue(group.balances.all { it.balanceCents == 0L && it.potCents == 0L && it.peerCents == 0L })
        assertTrue(group.transfers.isEmpty())

        // A member in expenses cannot be removed.
        val inUse = failureOf { anna.groups.removeMember(group.id, bas) }
        assertEquals(409, inUse.status)
        assertEquals("member_in_use", inUse.code)
        assertNotNull(inUse.message)

        // ---- Joining ----------------------------------------------------------------------

        // A second person sees the team's sport, joins with the code and claims "Bas"; not the pot.
        val bob = newDevice()
        bob.accounts.register(serverUrl, "Bob", "bob-${UUID.randomUUID()}@example.org", "geheim-wachtwoord")
        val invite = bob.groups.invite(group.inviteCode.lowercase())
        assertEquals("Heren 4", invite.name)
        assertEquals(Sport.HOCKEY, invite.sport)
        assertNull(invite.club)
        assertEquals(3, invite.memberCount)
        assertFalse(invite.alreadyMember)
        assertEquals(listOf("Bas", "Cas"), invite.unclaimedMembers.map { it.name })
        assertEquals(409, failureOf { bob.groups.join(group.inviteCode, claimMemberId = pot) }.status)
        val joined = bob.groups.join(group.inviteCode, claimMemberId = bas)
        assertEquals(bas, joined.myMemberId)
        assertEquals("Bob", joined.member(bas)?.name)
        assertTrue(bob.groups.invite(group.inviteCode).alreadyMember)

        // A wrong code and a bad password come back with the server's Dutch message.
        assertEquals("Deze uitnodigingscode klopt niet.", failureOf { bob.groups.invite("ZZZZZZZZ") }.message)
        val wrongPassword = failureOf { newDevice().accounts.login(serverUrl, email, "niet-het-wachtwoord") }
        assertEquals(401, wrongPassword.status)
        assertEquals("E-mailadres of wachtwoord klopt niet.", wrongPassword.message)

        // Logging in on another device, then logging out there, leaves this session alone.
        val tablet = newDevice()
        tablet.accounts.login(serverUrl, email, "geheim-wachtwoord")
        assertEquals(1, tablet.groups.groups().size)
        tablet.accounts.logout()
        assertFalse(tablet.sessionStore.current().isSignedIn)
        assertEquals("Anna", anna.accounts.profile().name)

        // Scanning is off on a server without an API key, and says so.
        if (!anna.receipts.isScanEnabled()) {
            assertEquals("scan_unavailable", failureOf { anna.receipts.scan(byteArrayOf(1, 2, 3)) }.code)
        }

        // When the penningmeester leaves, the longest-standing team member takes over the pot.
        assertEquals(bas, anna.groups.changeTreasurer(group.id, bas).treasurerMemberId)
        bob.groups.leaveGroup(group.id)
        assertTrue(bob.groups.groups().isEmpty())
        assertEquals(me, anna.groups.group(group.id).treasurerMemberId)

        // Deleting the accounts; the old token then ends the session with a 401.
        bob.accounts.deleteAccount()
        val stale = TestBackend(temporaryFolder.newFolder(), serverUrl).also(backends::add)
        stale.sessionStore.signIn(serverUrl, checkNotNull(anna.sessionStore.current().token))
        anna.accounts.deleteAccount()
        assertNull(anna.sessionStore.current().token)
        assertEquals(401, failureOf { stale.groups.groups() }.status)
        assertFalse(stale.sessionStore.current().isSignedIn)
    }

    private fun newDevice() = TestBackend(temporaryFolder.newFolder(), serverUrl).also(backends::add)

    private fun GroupDetail.balance(memberId: String) = balances.first { it.memberId == memberId }

    private suspend fun failureOf(call: suspend () -> Unit): ApiException.Server =
        try {
            call()
            fail("Expected the server to refuse")
            error("unreachable")
        } catch (e: ApiException.Server) {
            e
        }
}
