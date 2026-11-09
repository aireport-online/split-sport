package online.aireport.split

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import online.aireport.split.data.AccountRepository
import online.aireport.split.data.ExpenseRepository
import online.aireport.split.data.GroupChanges
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.ReceiptRepository
import online.aireport.split.data.api.ApiGateway
import online.aireport.split.data.api.SplitApiFactory
import online.aireport.split.data.receipt.ReceiptImageReader
import online.aireport.split.data.receipt.ReceiptPhotoFiles
import online.aireport.split.data.session.SessionStore

/** The app's dependencies, created once and handed to the view models. */
class AppContainer(context: Context) {
    private val sessionStore = SessionStore(
        dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SESSION_FILE) },
        defaultServerUrl = BuildConfig.DEFAULT_SERVER_URL,
    )
    private val gateway = ApiGateway(sessionStore, SplitApiFactory())

    val groupChanges = GroupChanges()
    val accountRepository = AccountRepository(gateway, sessionStore)
    val groupRepository = GroupRepository(gateway, groupChanges)
    val expenseRepository = ExpenseRepository(gateway, groupChanges)
    val receiptRepository = ReceiptRepository(gateway)
    val receiptImageReader = ReceiptImageReader(context.contentResolver)
    val receiptPhotoFiles = ReceiptPhotoFiles(context)

    private companion object {
        // Must match the file excluded from backups in res/xml/data_extraction_rules.xml.
        const val SESSION_FILE = "session"
    }
}
