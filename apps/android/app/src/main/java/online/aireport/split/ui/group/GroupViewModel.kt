package online.aireport.split.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.core.formatAmountInput
import online.aireport.split.core.parseAmount
import online.aireport.split.data.ExpenseRepository
import online.aireport.split.data.GroupChanges
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.Expense
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Transfer
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

/** The payment sheet: who paid whom how much, and when. */
data class SettlementForm(
    val fromMemberId: String,
    val toMemberId: String,
    val amountText: String,
    val date: LocalDate,
    val isSaving: Boolean = false,
    val error: UiText? = null,
) {
    val amountCents: Long? get() = parseAmount(amountText)

    val canSave: Boolean
        get() = fromMemberId != toMemberId && (amountCents ?: 0L) > 0L
}

data class GroupUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Set when the group could not be loaded and there is nothing to show. */
    val loadError: UiText? = null,
    val group: GroupDetail? = null,
    val expenses: List<Expense> = emptyList(),
    /** The open payment sheet, if any. */
    val settlement: SettlementForm? = null,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
)

class GroupViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val expenseRepository: ExpenseRepository,
    groupChanges: GroupChanges,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch { groupChanges.events.collect { load() } }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    /** Opens the payment sheet prefilled with a suggested payment. */
    fun onMarkAsPaid(transfer: Transfer) {
        _uiState.update {
            it.copy(
                settlement = SettlementForm(
                    fromMemberId = transfer.fromMemberId,
                    toMemberId = transfer.toMemberId,
                    amountText = formatAmountInput(transfer.amountCents),
                    date = LocalDate.now(),
                ),
            )
        }
    }

    fun onSettlementChange(form: SettlementForm) = _uiState.update { it.copy(settlement = form) }

    fun onSettlementDismiss() = _uiState.update { it.copy(settlement = null) }

    fun saveSettlement() {
        val form = _uiState.value.settlement ?: return
        val amountCents = form.amountCents
        if (!form.canSave || form.isSaving || amountCents == null) return
        onSettlementChange(form.copy(isSaving = true, error = null))
        viewModelScope.launch {
            try {
                expenseRepository.settle(groupId, form.fromMemberId, form.toMemberId, amountCents, form.date)
                _uiState.update { it.copy(settlement = null) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(settlement = form.copy(isSaving = false, error = e.toUiText())) }
            }
        }
    }

    /** Removes a payment, a fine or a contribution. */
    fun deleteEntry(expenseId: String) {
        viewModelScope.launch {
            try {
                expenseRepository.deleteExpense(groupId, expenseId)
            } catch (e: ApiException) {
                _uiState.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                val (group, expenses) = coroutineScope {
                    val group = async { groupRepository.group(groupId) }
                    val expenses = async { expenseRepository.expenses(groupId) }
                    group.await() to expenses.await()
                }
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = null, group = group, expenses = expenses)
                }
            } catch (e: ApiException) {
                _uiState.update { state ->
                    // Keep showing what was loaded before; only an empty screen turns into an error.
                    if (state.group == null) {
                        state.copy(isLoading = false, isRefreshing = false, loadError = e.toUiText())
                    } else {
                        state.copy(isRefreshing = false, message = e.toUiText())
                    }
                }
            }
        }
    }
}
