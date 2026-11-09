package online.aireport.split.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists the session token and the server URL. */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val defaultServerUrl: String,
) {
    val session: Flow<Session> = dataStore.data
        .map { preferences ->
            Session(
                serverUrl = preferences[SERVER_URL] ?: defaultServerUrl,
                token = preferences[TOKEN],
            )
        }
        .distinctUntilChanged()

    suspend fun current(): Session = session.first()

    suspend fun signIn(serverUrl: String, token: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_URL] = serverUrl
            preferences[TOKEN] = token
        }
    }

    /** Forgets the token; the server URL stays so the login screen points at the same server. */
    suspend fun signOut() {
        dataStore.edit { preferences -> preferences.remove(TOKEN) }
    }

    /** Forgets [token] unless another session has replaced it in the meantime. */
    suspend fun signOutIfCurrent(token: String) {
        dataStore.edit { preferences ->
            if (preferences[TOKEN] == token) preferences.remove(TOKEN)
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
    }
}
