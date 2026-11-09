package online.aireport.split.data.api

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import online.aireport.split.data.session.Session
import online.aireport.split.data.session.SessionStore
import retrofit2.HttpException

/**
 * The single door to the Split API: picks the server and token of the current session, turns
 * every failure into an [ApiException] and ends the session when the server no longer accepts
 * its token.
 */
class ApiGateway(
    private val sessionStore: SessionStore,
    private val apiFactory: SplitApiFactory,
) {
    @Volatile
    private var cached: Pair<Session, SplitApi>? = null

    /** Calls the API as the current session. */
    suspend fun <T> call(request: suspend SplitApi.() -> T): T {
        val session = sessionStore.current()
        return execute(session, request)
    }

    /** Calls [serverUrl] without a token, for logging in and registering. */
    suspend fun <T> callAnonymously(serverUrl: String, request: suspend SplitApi.() -> T): T =
        execute(Session(serverUrl, token = null), request)

    private suspend fun <T> execute(session: Session, request: suspend SplitApi.() -> T): T =
        try {
            apiFor(session).request()
        } catch (e: HttpException) {
            val failure = e.toApiException()
            if (failure.status == HTTP_UNAUTHORIZED && session.token != null) {
                sessionStore.signOutIfCurrent(session.token)
            }
            throw failure
        } catch (e: IOException) {
            throw ApiException.Network(e)
        } catch (e: SerializationException) {
            throw ApiException.UnexpectedResponse(e)
        }

    private fun apiFor(session: Session): SplitApi {
        cached?.let { (cachedSession, api) -> if (cachedSession == session) return api }
        val baseUrl = ServerUrl.apiBaseUrl(session.serverUrl) ?: throw ApiException.InvalidServerUrl()
        return apiFactory.create(baseUrl, session.token).also { cached = session to it }
    }

    private fun HttpException.toApiException(): ApiException.Server {
        val error = try {
            response()?.errorBody()?.string()?.let { SplitJson.decodeFromString<ErrorBody>(it).error }
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }
        return ApiException.Server(status = code(), code = error?.code, message = error?.message)
    }

    @Serializable
    private class ErrorBody(val error: ErrorDetail)

    @Serializable
    private class ErrorDetail(val code: String, val message: String)

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
