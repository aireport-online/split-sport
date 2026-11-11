package online.aireport.split.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.data.GroupChanges
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.NewTeam
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.GroupSummary
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

data class GroupsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Set when the teams could not be loaded and there is nothing to show. */
    val loadError: UiText? = null,
    val groups: List<GroupSummary> = emptyList(),
    val isCreating: Boolean = false,
    /** The team that was just created and should be opened. */
    val createdGroupId: String? = null,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
) {
    /** The balance over all teams. */
    val totalBalanceCents: Long get() = groups.sumOf { it.myBalanceCents }
}

class GroupsViewModel(
    private val groupRepository: GroupRepository,
    groupChanges: GroupChanges,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

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

    fun createTeam(team: NewTeam) {
        if (_uiState.value.isCreating) return
        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                val group = groupRepository.createGroup(team)
                _uiState.update { it.copy(isCreating = false, createdGroupId = group.id) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isCreating = false, message = e.toUiText()) }
            }
        }
    }

    fun onCreatedGroupOpened() = _uiState.update { it.copy(createdGroupId = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                val groups = groupRepository.groups()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, loadError = null, groups = groups) }
            } catch (e: ApiException) {
                _uiState.update { state ->
                    // Keep showing what was loaded before; only an empty screen turns into an error.
                    if (state.isLoading) {
                        state.copy(isLoading = false, isRefreshing = false, loadError = e.toUiText())
                    } else {
                        state.copy(isRefreshing = false, message = e.toUiText())
                    }
                }
            }
        }
    }
}
