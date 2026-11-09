package online.aireport.split.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.api.SplitApiFactory
import online.aireport.split.data.session.SessionStore

/**
 * The app's data layer as `AppContainer` wires it, but with the session stored in [directory]
 * instead of the app's files, so it runs on the JVM.
 */
class TestBackend(directory: File, defaultServerUrl: String) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionStore = SessionStore(
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "session.preferences_pb") },
        defaultServerUrl = defaultServerUrl,
    )
    val gateway = ApiGateway(sessionStore, SplitApiFactory())
    val groupChanges = GroupChanges()
    val accounts = AccountRepository(gateway, sessionStore)
    val groups = GroupRepository(gateway, groupChanges)
    val expenses = ExpenseRepository(gateway, groupChanges)
    val receipts = ReceiptRepository(gateway)

    override fun close() = scope.cancel()
}
