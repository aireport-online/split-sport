package online.aireport.split.data

import kotlinx.coroutines.flow.Flow
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.api.ServerUrl
import online.aireport.split.data.model.AuthResponse
import online.aireport.split.data.model.LoginRequest
import online.aireport.split.data.model.RegisterRequest
import online.aireport.split.data.model.UpdateProfileRequest
import online.aireport.split.data.model.User
import online.aireport.split.data.session.Session
import online.aireport.split.data.session.SessionStore

/** Signing in and out, and the signed-in person's own profile. */
class AccountRepository(
    private val gateway: ApiGateway,
    private val sessionStore: SessionStore,
) {
    val session: Flow<Session> = sessionStore.session

    suspend fun login(serverUrl: String, email: String, password: String) {
        signIn(serverUrl) { url ->
            gateway.callAnonymously(url) { login(LoginRequest(email.trim(), password)) }
        }
    }

    suspend fun register(serverUrl: String, name: String, email: String, password: String) {
        signIn(serverUrl) { url ->
            gateway.callAnonymously(url) { register(RegisterRequest(name.trim(), email.trim(), password)) }
        }
    }

    /** Ends the session on the server when it can be reached, and always on this device. */
    suspend fun logout() {
        try {
            gateway.call { logout() }
        } catch (_: ApiException) {
            // The token is dropped locally either way; an unreachable server must not trap the user.
        }
        sessionStore.signOut()
    }

    suspend fun profile(): User = gateway.call { me() }

    /** An empty [iban] clears it. */
    suspend fun updateProfile(name: String, iban: String): User =
        gateway.call { updateMe(UpdateProfileRequest(name.trim(), iban.trim())) }

    suspend fun deleteAccount() {
        gateway.call { deleteMe() }
        sessionStore.signOut()
    }

    private suspend fun signIn(serverUrl: String, authenticate: suspend (String) -> AuthResponse) {
        val url = ServerUrl.normalize(serverUrl) ?: throw ApiException.InvalidServerUrl()
        sessionStore.signIn(url, authenticate(url).token)
    }
}
