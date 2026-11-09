package online.aireport.split.data

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import online.aireport.split.core.Split
import online.aireport.split.core.SplitItem
import online.aireport.split.data.api.ApiException
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
    fun `an empty answer is fine for calls that return nothing`() = runTest {
        backend.sessionStore.signIn(serverUrl, "t")
        server.enqueue(MockResponse(code = 204))

        backend.groups.leaveGroup("g1")

        assertEquals("/api/groups/g1/leave", server.takeRequest().url.encodedPath)
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
