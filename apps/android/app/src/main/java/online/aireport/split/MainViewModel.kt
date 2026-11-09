package online.aireport.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import online.aireport.split.data.AccountRepository

/** App-wide state: whether someone is signed in, and an invitation link waiting to be opened. */
class MainViewModel(accountRepository: AccountRepository) : ViewModel() {

    /** Null until the stored session has been read. */
    val isSignedIn: StateFlow<Boolean?> = accountRepository.session
        .map { it.isSignedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val _pendingInviteCode = MutableStateFlow<String?>(null)

    /** The code of a `split://join/<code>` link that has not been shown yet. */
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    fun onInviteLink(code: String) {
        _pendingInviteCode.value = code
    }

    fun onInviteHandled() {
        _pendingInviteCode.value = null
    }
}
