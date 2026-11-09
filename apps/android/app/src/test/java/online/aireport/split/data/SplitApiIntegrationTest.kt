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
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.GroupColor
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
 * Runs the data layer against a live Split server. Skipped unless `SPLIT_API_URL` is set:
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
    fun `a group from registration to a settled balance and a deleted account`() = runBlocking {
        val anna = newDevice()
        val email = "anna-${UUID.randomUUID()}@example.org"

        // Account
        anna.accounts.register(serverUrl, "Anna", email, "geheim-wachtwoord")
        assertTrue(anna.sessionStore.current().isSignedIn)
        assertEquals("Anna", anna.accounts.profile().name)
        assertEquals("NL91ABNA0417164300", anna.accounts.updateProfile("Anna", "nl91 abna 0417 1643 00").iban)
        assertTrue(anna.groups.groups().isEmpty())

        // Group and members without an account
        var group = anna.groups.createGroup("Weekendje Texel", GroupColor.TEAL)
        assertEquals(GroupColor.TEAL, group.color)
        group = anna.groups.addMember(group.id, "Bas")
        group = anna.groups.addMember(group.id, "Cas")
        group = anna.groups.addMember(group.id, "Dirk")
        val (me, bas, cas, dirk) = group.members.map { it.id }
        assertEquals(me, group.myMemberId)
        group = anna.groups.renameMember(group.id, dirk, "Dirk-Jan")
        assertEquals("Dirk-Jan", group.member(dirk)?.name)
        group = anna.groups.removeMember(group.id, dirk)
        assertEquals(listOf("Anna", "Bas", "Cas"), group.members.map { it.name })
        assertEquals("Texel", anna.groups.renameGroup(group.id, "Texel").name)
        assertEquals(GroupColor.ROSE, anna.groups.recolorGroup(group.id, GroupColor.ROSE).color)

        // One expense per split mode; the server must agree with the local computeShares.
        val splits = listOf(
            1000L to Split.Equal(listOf(me, bas, cas)),
            5000L to Split.Exact(listOf(Share(me, 1250), Share(bas, 0), Share(cas, 3750))),
            1000L to Split.Shares(listOf(Weight(me, 1), Weight(bas, 2), Weight(cas, 0))),
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
        assertEquals(created.map { it.id }.toSet(), anna.expenses.expenses(group.id).map { it.id }.toSet())
        assertEquals(created[3], anna.expenses.expense(group.id, created[3].id))

        // Edit: the equal split becomes 12,00 over two people.
        val edited = anna.expenses.updateExpense(
            group.id,
            created[0].id,
            ExpenseInput("Boodschappen", 1200, today.minusDays(1), paidById = bas, split = Split.Equal(listOf(me, bas))),
        )
        assertEquals("Boodschappen", edited.description)
        assertEquals(bas, edited.paidById)
        assertEquals(listOf(600L, 600L), edited.shares.map { it.amountCents })

        // Balances add up to zero and Anna, who paid nearly everything, gets money back.
        group = anna.groups.group(group.id)
        assertEquals(0L, group.balances.sumOf { it.balanceCents })
        assertEquals(9700L, group.totalSpentCents)
        val myBalance = group.balances.first { it.memberId == me }.balanceCents
        assertTrue("Anna's balance is $myBalance", myBalance > 0)
        assertEquals(myBalance, anna.groups.groups().single().myBalanceCents)
        assertTrue(group.transfers.isNotEmpty())

        // Settle every suggested payment: everyone is square.
        for (transfer in group.transfers) {
            val settlement = anna.expenses.settle(group.id, transfer.fromMemberId, transfer.toMemberId, transfer.amountCents, today)
            assertEquals(ExpenseKind.SETTLEMENT, settlement.kind)
            assertEquals(transfer.toMemberId, settlement.shares.single().memberId)
        }
        group = anna.groups.group(group.id)
        assertTrue(group.balances.all { it.balanceCents == 0L })
        assertTrue(group.transfers.isEmpty())

        // Deleting an expense brings the debt back; a member in expenses cannot be removed.
        anna.expenses.deleteExpense(group.id, created[1].id)
        assertFalse(anna.groups.group(group.id).transfers.isEmpty())
        val inUse = failureOf { anna.groups.removeMember(group.id, bas) }
        assertEquals(409, inUse.status)
        assertEquals("member_in_use", inUse.code)
        assertNotNull(inUse.message)

        // A second person joins with the invite code and claims "Bas".
        val bob = newDevice()
        bob.accounts.register(serverUrl, "Bob", "bob-${UUID.randomUUID()}@example.org", "geheim-wachtwoord")
        val invite = bob.groups.invite(group.inviteCode.lowercase())
        assertEquals("Texel", invite.name)
        assertFalse(invite.alreadyMember)
        assertEquals(listOf("Bas", "Cas"), invite.unclaimedMembers.map { it.name })
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

        // Leaving and deleting the accounts; the old token then ends the session with a 401.
        bob.groups.leaveGroup(group.id)
        assertTrue(bob.groups.groups().isEmpty())
        bob.accounts.deleteAccount()
        val stale = TestBackend(temporaryFolder.newFolder(), serverUrl).also(backends::add)
        stale.sessionStore.signIn(serverUrl, checkNotNull(anna.sessionStore.current().token))
        anna.accounts.deleteAccount()
        assertNull(anna.sessionStore.current().token)
        assertEquals(401, failureOf { stale.groups.groups() }.status)
        assertFalse(stale.sessionStore.current().isSignedIn)
    }

    private fun newDevice() = TestBackend(temporaryFolder.newFolder(), serverUrl).also(backends::add)

    private suspend fun failureOf(call: suspend () -> Unit): ApiException.Server =
        try {
            call()
            fail("Expected the server to refuse")
            error("unreachable")
        } catch (e: ApiException.Server) {
            e
        }
}
