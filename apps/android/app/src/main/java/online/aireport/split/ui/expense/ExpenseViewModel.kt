package online.aireport.split.ui.expense

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import online.aireport.split.R
import online.aireport.split.data.ExpenseInput
import online.aireport.split.data.ExpenseRepository
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.ReceiptRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.api.SplitJson
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.receipt.ReceiptImageReader
import online.aireport.split.data.receipt.ReceiptPhotoFiles
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

data class ExpenseUiState(
    val isEditing: Boolean,
    val isLoading: Boolean = true,
    /** Set when the form could not be loaded. */
    val loadError: UiText? = null,
    val group: GroupDetail? = null,
    val draft: ExpenseDraft? = null,
    /** Whether the server can read receipts; the scan button is hidden otherwise. */
    val scanEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
    /** The expense was saved or deleted; the screen can close. */
    val isDone: Boolean = false,
) {
    val isBusy: Boolean get() = isSaving || isDeleting || isScanning

    /** The live "Per persoon" result; null until the form is loaded. */
    val preview: SplitPreview?
        get() = if (group != null && draft != null) draft.preview(group.members.map { it.id }) else null

    val canSave: Boolean
        get() = !isBusy && draft?.description?.isNotBlank() == true && preview is SplitPreview.Valid
}

class ExpenseViewModel(
    private val groupId: String,
    private val expenseId: String?,
    private val savedState: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    private val receiptRepository: ReceiptRepository,
    private val receiptImageReader: ReceiptImageReader,
    private val receiptPhotoFiles: ReceiptPhotoFiles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpenseUiState(isEditing = expenseId != null))
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun onDraftChange(draft: ExpenseDraft) {
        savedState[KEY_DRAFT] = SplitJson.encodeToString(draft)
        _uiState.update { it.copy(draft = draft) }
    }

    /** A `content://` URI for the camera app to write a photo of the receipt to. */
    fun newPhotoUri(): Uri = receiptPhotoFiles.newPhotoUri()

    /** Reads the receipt on the photo at [uri] and fills the form with its lines. */
    fun scanReceipt(uri: Uri) {
        viewModelScope.launch {
            // After the app was killed behind the camera the photo arrives before the form has loaded.
            val loaded = _uiState.first { !it.isLoading }
            val group = loaded.group ?: return@launch
            if (loaded.isBusy) return@launch
            _uiState.update { it.copy(isScanning = true) }
            val jpeg = receiptImageReader.readJpeg(uri)
            receiptPhotoFiles.deleteAll()
            val message = if (jpeg == null) UiText.Resource(R.string.scan_unreadable) else applyScan(jpeg, group)
            _uiState.update { it.copy(isScanning = false, message = message) }
        }
    }

    fun onCameraUnavailable() = _uiState.update { it.copy(message = UiText.Resource(R.string.scan_no_camera)) }

    fun save() {
        val state = _uiState.value
        val draft = state.draft ?: return
        val preview = state.preview as? SplitPreview.Valid ?: return
        if (!state.canSave) return
        val input = ExpenseInput(draft.description, preview.amountCents, draft.date, draft.paidById, preview.split)
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (expenseId == null) {
                    expenseRepository.createExpense(groupId, input)
                } else {
                    expenseRepository.updateExpense(groupId, expenseId, input)
                }
                _uiState.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isSaving = false, message = e.toUiText()) }
            }
        }
    }

    fun delete() {
        if (expenseId == null || _uiState.value.isBusy) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(groupId, expenseId)
                _uiState.update { it.copy(isDeleting = false, isDone = true) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isDeleting = false, message = e.toUiText()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val group = async { groupRepository.group(groupId) }
                    val expense = async { expenseId?.let { expenseRepository.expense(groupId, it) } }
                    val scanEnabled = async { isScanEnabled() }
                    val today = LocalDate.now()
                    val draft = restoredDraft()
                        ?: expense.await()?.let { expenseDraft(it, group.await(), today) }
                        ?: newExpenseDraft(group.await(), today)
                    val loadedGroup = group.await()
                    val canScan = scanEnabled.await()
                    _uiState.update {
                        it.copy(isLoading = false, group = loadedGroup, draft = draft, scanEnabled = canScan)
                    }
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toUiText()) }
            }
        }
    }

    /** Scanning is an extra: when the server cannot say whether it is on, the form works without. */
    private suspend fun isScanEnabled(): Boolean =
        try {
            receiptRepository.isScanEnabled()
        } catch (_: ApiException) {
            false
        }

    /** The form as it was before the process was killed, if it was. */
    private fun restoredDraft(): ExpenseDraft? =
        savedState.get<String>(KEY_DRAFT)?.let { json ->
            try {
                SplitJson.decodeFromString<ExpenseDraft>(json)
            } catch (_: SerializationException) {
                null
            }
        }

    /** Returns a message when the scan failed or found nothing. */
    private suspend fun applyScan(jpeg: ByteArray, group: GroupDetail): UiText? =
        try {
            val scan = receiptRepository.scan(jpeg)
            // The lines of a receipt are for the people; the pot can be ticked by hand.
            _uiState.value.draft?.let { onDraftChange(it.withReceipt(scan, group.players.map { m -> m.id })) }
            UiText.Resource(R.string.scan_no_lines).takeIf { scan.items.none { it.amountCents > 0 } }
        } catch (e: ApiException) {
            e.toUiText()
        }

    private companion object {
        const val KEY_DRAFT = "draft"
    }
}
