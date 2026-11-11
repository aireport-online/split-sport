package online.aireport.split.ui.charge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.data.ExpenseRepository
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.ChargeType
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

data class ChargeUiState(
    val type: ChargeType,
    val isLoading: Boolean = true,
    /** Set when the team could not be loaded. */
    val loadError: UiText? = null,
    val group: GroupDetail? = null,
    val form: ChargeForm? = null,
    val isSaving: Boolean = false,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
    /** The charge was saved; the screen can close. */
    val isDone: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && form?.canSave == true
}

/** "Boete geven" and "Inleg vragen": charges team members an amount each, owed to the pot. */
class ChargeViewModel(
    private val groupId: String,
    type: ChargeType,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChargeUiState(type))
    val uiState: StateFlow<ChargeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun onFormChange(form: ChargeForm) = _uiState.update { it.copy(form = form) }

    fun save() {
        val state = _uiState.value
        val form = state.form ?: return
        val amountCents = form.amountCents ?: return
        val group = state.group ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        // Charged in the team's member order, whatever the order of ticking.
        val memberIds = group.players.map { it.id }.filter { it in form.memberIds }
        viewModelScope.launch {
            try {
                expenseRepository.charge(groupId, form.type, form.description, amountCents, memberIds, form.date)
                _uiState.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isSaving = false, message = e.toUiText()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                val group = groupRepository.group(groupId)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        group = group,
                        form = state.form ?: newChargeForm(state.type, group, LocalDate.now()),
                    )
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toUiText()) }
            }
        }
    }
}
