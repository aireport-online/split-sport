package online.aireport.split.data

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import online.aireport.split.core.Split
import online.aireport.split.core.SplitItem
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.ExpenseKind
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.PotStatus
import online.aireport.split.data.model.Sport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The data layer against a scripted server: what goes over the wire and how failures surface. */
class ApiGatewayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var backend: TestBackend
    private lateinit var serverUrl: String

    @Before
    fun setUp() {
        server.start()
        serverUrl = server.url("/").toString().trimEnd('/')
        backend = TestBackend(temporaryFolder.root, defaultServerUrl = serverUrl)
    }

    @After
    fun tearDown() {
        backend.close()
        server.close()
    }

    @Test
    fun `logging in stores the token and later calls send it`() = runTest {
        server.enqueue(json("""{"token":"t-123","user":{"id":"u1","name":"Anna","email":"anna@example.org","iban":null}}"""))
        server.enqueue(json("""{"groups":[]}"""))

        backend.accounts.login(serverUrl, " anna@example.org ", "geheim123")
        backend.groups.groups()

        val login = server.takeRequest()
        assertEquals("/api/auth/login", login.url.encodedPath)
        assertNull(login.headers["Authorization"])
        assertEquals("""{"email":"anna@example.org","password":"geheim123"}""", login.body?.utf8())
        val groups = server.takeRequest()
        assertEquals("/api/groups", groups.url.encodedPath)
        assertEquals("Bearer t-123", groups.headers["Authorization"])
    }

    @Test
    fun `the server's message is passed on`() = runTest {
        server.enqueue(error(409, "email_taken", "Er bestaat al een account met dit e-mailadres."))

        val failure = failureOf { backend.accounts.register(serverUrl, "Anna", "anna@example.org", "geheim123") }

        failure as ApiException.Server
        assertEquals(409, failure.status)
        assertEquals("email_taken", failure.code)
        assertEquals("Er bestaat al een account met dit e-mailadres.", failure.message)
        assertFalse(backend.sessionStore.current().isSignedIn)
    }

    @Test
    fun `a 401 ends the session`() = runTest {
        backend.sessionStore.signIn(serverUrl, "expired")
        server.enqueue(error(401, "unauthorized", "Log opnieuw in."))

        val failure = failureOf { backend.groups.groups() }

        assertEquals(401, (failure as ApiException.Server).status)
        val session = backend.sessionStore.current()
        assertFalse(session.isSignedIn)
        assertEquals("The server URL is kept for the login screen", serverUrl, session.serverUrl)
    }

    @Test
    fun `a 401 for an old token leaves a newer session alone`() = runTest {
        backend.sessionStore.signIn(serverUrl, "new")

        backend.sessionStore.signOutIfCurrent("old")

        assertTrue(backend.sessionStore.current().isSignedIn)
    }

    @Test
    fun `an error that is not from Split has no message`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(MockResponse(code = 502, body = "<html>Bad Gateway</html>"))

        val failure = failureOf { backend.groups.groups() } as ApiException.Server

        assertEquals(502, failure.status)
        assertNull(failure.message)
    }

    @Test
    fun `an answer that cannot be read is an unexpected response`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(json("""{"unexpected":true}"""))

        assertTrue(failureOf { backend.groups.groups() } is ApiException.UnexpectedResponse)
    }

    @Test
    fun `an unreachable server is a network failure`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.close()

        assertTrue(failureOf { backend.groups.groups() } is ApiException.Network)
    }

    @Test
    fun `a server address that is not a URL is rejected before any request`() = runTest {
        assertTrue(failureOf { backend.accounts.login("http://", "a@b.nl", "geheim123") } is ApiException.InvalidServerUrl)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logging out forgets the token even when the server cannot be reached`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.close()

        backend.accounts.logout()

        assertFalse(backend.sessionStore.current().isSignedIn)
    }

    @Test
    fun `an expense is sent with only the list of its split mode`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(
            json(
                """{"id":"e1","groupId":"g1","kind":"expense","description":"Café","amountCents":1100,
                   "paidById":"m1","date":"2026-11-02","splitMode":"items",
                   "shares":[{"memberId":"m1","amountCents":1100,"weight":null}],
                   "items":[{"name":"Pizza","amountCents":1000,"memberIds":["m1"]}],
                   "createdAt":"2026-11-02T10:00:00.000Z","updatedAt":"2026-11-02T10:00:00.000Z"}""",
                code = 201,
            ),
        )
        val input = ExpenseInput(
            description = " Café ",
            amountCents = 1100,
            date = LocalDate.of(2026, 11, 2),
            paidById = "m1",
            split = Split.Items(listOf(SplitItem("Pizza", 1000, listOf("m1")))),
        )

        val expense = backend.expenses.createExpense("g1", input)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/groups/g1/expenses", request.url.encodedPath)
        assertEquals(
            """{"description":"Café","amountCents":1100,"date":"2026-11-02","paidById":"m1",""" +
                """"split":{"mode":"items","items":[{"name":"Pizza","amountCents":1000,"memberIds":["m1"]}]}}""",
            request.body?.utf8(),
        )
        assertEquals(1100L, expense.shares.single().amountCents)
    }

    @Test
    fun `a charge is sent with its type and the amount per member`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(
            json(
                """{"id":"e2","groupId":"g1","kind":"fine","description":"Te laat","amountCents":400,
                   "paidById":"pot","date":"2026-11-02","splitMode":"exact",
                   "shares":[{"memberId":"m1","amountCents":200,"weight":null},{"memberId":"m2","amountCents":200,"weight":null}],
                   "items":[],"createdAt":"2026-11-02T10:00:00.000Z","updatedAt":"2026-11-02T10:00:00.000Z"}""",
                code = 201,
            ),
        )

        val fine = backend.expenses.charge("g1", ChargeType.FINE, " Te laat ", 200, listOf("m1", "m2"), LocalDate.of(2026, 11, 2))

        val request = server.takeRequest()
        assertEquals("/api/groups/g1/charges", request.url.encodedPath)
        assertEquals(
            """{"type":"fine","description":"Te laat","amountCents":200,"memberIds":["m1","m2"],"date":"2026-11-02"}""",
            request.body?.utf8(),
        )
        assertEquals(ExpenseKind.FINE, fine.kind)
        assertEquals(400L, fine.amountCents)
    }

    @Test
    fun `a team is created with its sport, and without a club when none was given`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(json(TEAM_JSON, code = 201))

        val team = backend.groups.createGroup(NewTeam(" Dames 2 ", GroupColor.ROSE, Sport.HOCKEY, club = "  ", season = "2026/27"))

        assertEquals(
            """{"name":"Dames 2","color":"rose","sport":"hockey","season":"2026/27"}""",
            server.takeRequest().body?.utf8(),
        )
        assertEquals("pot", team.potMemberId)
        assertEquals(listOf("Sanne"), team.players.map { it.name })
        assertTrue(team.members.last().isPot)
        assertEquals(PotStatus(cashCents = 4750, toReceiveCents = 3900, toPayCents = 4550), team.pot)
    }

    @Test
    fun `clearing the club sends an empty text, which the server stores as none`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(json(TEAM_JSON))

        backend.groups.updateDetails("g1", "Dames 2", club = "", season = "2026/27")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("""{"name":"Dames 2","club":"","season":"2026/27"}""", request.body?.utf8())
    }

    @Test
    fun `a sport from a newer server is read as overig`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(
            json(
                """{"groups":[{"id":"g9","name":"Schaakclub","color":"slate","sport":"schaken","club":null,"season":null,
                   "currency":"EUR","memberCount":2,"myMemberId":"m1","myBalanceCents":0,"lastActivityAt":"2026-11-02T10:00:00.000Z"}]}""",
            ),
        )

        val team = backend.groups.groups().single()

        assertEquals(Sport.OVERIG, team.sport)
        assertNull(team.club)
    }

    @Test
    fun `an empty answer is fine for calls that return nothing`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(MockResponse(code = 204))

        backend.groups.leaveGroup("g1")

        assertEquals("/api/groups/g1/leave", server.takeRequest().url.encodedPath)
    }

    private companion object {
        /** A team as the server answers it, trimmed to one player and the pot. */
        const val TEAM_JSON = """{"id":"g1","name":"Dames 2","color":"rose","sport":"hockey","club":"MHC Oostpoort",
            "season":"2026/27","currency":"EUR","inviteCode":"K7WQ2MXD","myMemberId":"m1","potMemberId":"pot",
            "treasurerMemberId":"m1",
            "members":[{"id":"m1","name":"Sanne","kind":"player","userId":"u1","iban":null},
                       {"id":"pot","name":"Teamkas","kind":"pot","userId":null,"iban":null}],
            "balances":[{"memberId":"m1","balanceCents":0,"potCents":0,"peerCents":0},
                        {"memberId":"pot","balanceCents":0,"potCents":0,"peerCents":0}],
            "transfers":[],"pot":{"cashCents":4750,"toReceiveCents":3900,"toPayCents":4550},
            "fines":[{"id":"f1","name":"Te laat","amountCents":200}],"fineStats":[],"totalSpentCents":0}"""
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse(code = code, headers = Headers.headersOf("Content-Type", "application/json"), body = body)

    private fun error(status: Int, code: String, message: String) =
        json("""{"error":{"code":"$code","message":"$message"}}""", status)

    private suspend fun failureOf(call: suspend () -> Unit): ApiException =
        try {
            call()
            fail("Expected the call to fail")
            error("unreachable")
        } catch (e: ApiException) {
            e
        }
}
