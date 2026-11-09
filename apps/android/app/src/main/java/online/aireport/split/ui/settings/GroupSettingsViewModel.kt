package online.aireport.split.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.core.InviteCode
import online.aireport.split.data.AccountRepository
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

data class GroupSettingsUiState(
    val isLoading: Boolean = true,
    /** Set when the group could not be loaded. */
    val loadError: UiText? = null,
    val group: GroupDetail? = null,
    /** `<server>/join/<code>`, for the share sheet. */
    val inviteLink: String? = null,
    /** A change is being saved. */
    val isBusy: Boolean = false,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
    /** The signed-in person left the group; the screen can close. */
    val hasLeft: Boolean = false,
)

class GroupSettingsViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupSettingsUiState())
    val uiState: StateFlow<GroupSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun rename(name: String) = change { groupRepository.renameGroup(groupId, name) }

    fun recolor(color: GroupColor) = change { groupRepository.recolorGroup(groupId, color) }

    fun addMember(name: String) = change { groupRepository.addMember(groupId, name) }

    fun renameMember(memberId: String, name: String) = change { groupRepository.renameMember(groupId, memberId, name) }

    fun removeMember(memberId: String) = change { groupRepository.removeMember(groupId, memberId) }

    fun leave() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                groupRepository.leaveGroup(groupId)
                _uiState.update { it.copy(isBusy = false, hasLeft = true) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isBusy = false, message = e.toUiText()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                val group = groupRepository.group(groupId)
                val serverUrl = accountRepository.session.first().serverUrl
                _uiState.update {
                    it.copy(isLoading = false, group = group, inviteLink = InviteCode.shareLink(serverUrl, group.inviteCode))
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toUiText()) }
            }
        }
    }

    /** Runs a change that answers with the updated group. */
    private fun change(request: suspend () -> GroupDetail) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val group = request()
                _uiState.update { it.copy(isBusy = false, group = group) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isBusy = false, message = e.toUiText()) }
            }
        }
    }
}
