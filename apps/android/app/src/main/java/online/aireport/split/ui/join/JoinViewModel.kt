package online.aireport.split.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.core.InviteCode
import online.aireport.split.data.GroupRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.InvitePreview
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

/** The answer to "Wie ben jij?". */
sealed interface Identity {
    /** An existing member without an account. */
    data class ExistingMember(val memberId: String) : Identity

    /** "Ik sta er niet tussen": join as a new member. */
    data object NewMember : Identity
}

data class JoinUiState(
    val code: String = "",
    val isLookingUp: Boolean = false,
    /** The group behind the code, once it has been looked up. */
    val invite: InvitePreview? = null,
    val identity: Identity? = null,
    val isJoining: Boolean = false,
    val error: UiText? = null,
    /** The group that was joined (or already was) and should be opened. */
    val openGroupId: String? = null,
) {
    val canLookUp: Boolean get() = InviteCode.isValid(code) && !isLookingUp

    /** "Wie ben jij?" is only asked when the group has members without an account. */
    val asksIdentity: Boolean get() = invite?.alreadyMember == false && invite.unclaimedMembers.isNotEmpty()

    val canJoin: Boolean get() = invite != null && !isJoining && (!asksIdentity || identity != null)
}

class JoinViewModel(
    initialCode: String?,
    private val groupRepository: GroupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(JoinUiState(code = InviteCode.sanitize(initialCode.orEmpty())))
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    init {
        // Arriving by link: show the group right away.
        if (_uiState.value.canLookUp) lookUp()
    }

    /** Accepts a typed code as well as a pasted share link. */
    fun onCodeChange(input: String) {
        val code = InviteCode.fromLink(input) ?: InviteCode.sanitize(input)
        _uiState.update { it.copy(code = code, invite = null, identity = null, error = null) }
    }

    fun lookUp() {
        val code = _uiState.value.code
        if (!_uiState.value.canLookUp) return
        _uiState.update { it.copy(isLookingUp = true, error = null) }
        viewModelScope.launch {
            try {
                val invite = groupRepository.invite(code)
                _uiState.update { it.copy(isLookingUp = false, invite = invite) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLookingUp = false, error = e.toUiText()) }
            }
        }
    }

    fun onIdentityChange(identity: Identity) = _uiState.update { it.copy(identity = identity) }

    fun join() {
        val state = _uiState.value
        val invite = state.invite ?: return
        if (!state.canJoin) return
        if (invite.alreadyMember) {
            _uiState.update { it.copy(openGroupId = invite.groupId) }
            return
        }
        _uiState.update { it.copy(isJoining = true, error = null) }
        viewModelScope.launch {
            try {
                val claimMemberId = (state.identity as? Identity.ExistingMember)?.memberId
                val group = groupRepository.join(state.code, claimMemberId)
                _uiState.update { it.copy(isJoining = false, openGroupId = group.id) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isJoining = false, error = e.toUiText()) }
            }
        }
    }

    fun onGroupOpened() = _uiState.update { it.copy(openGroupId = null) }
}
