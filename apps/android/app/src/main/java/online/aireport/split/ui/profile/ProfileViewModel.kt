package online.aireport.split.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.R
import online.aireport.split.core.formatIban
import online.aireport.split.data.AccountRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.data.model.User
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

data class ProfileUiState(
    val isLoading: Boolean = true,
    /** Set when the profile could not be loaded. */
    val loadError: UiText? = null,
    val user: User? = null,
    val name: String = "",
    val iban: String = "",
    val isSaving: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isDeleting: Boolean = false,
    /** A one-off message for the snackbar. */
    val message: UiText? = null,
) {
    val isBusy: Boolean get() = isSaving || isLoggingOut || isDeleting

    val hasChanges: Boolean
        get() = user != null && (name.trim() != user.name || iban.compact() != user.iban.orEmpty())

    val canSave: Boolean get() = !isBusy && hasChanges && name.isNotBlank()
}

private fun String.compact(): String = filterNot { it.isWhitespace() }.uppercase()

class ProfileViewModel(private val accountRepository: AccountRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        load()
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onIbanChange(iban: String) = _uiState.update { it.copy(iban = iban) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val user = accountRepository.updateProfile(state.name, state.iban)
                _uiState.update { it.withUser(user).copy(isSaving = false, message = UiText.Resource(R.string.profile_saved)) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isSaving = false, message = e.toUiText()) }
            }
        }
    }

    /** Ends the session; the app then shows the login screen. */
    fun logout() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch { accountRepository.logout() }
    }

    /** Deletes the account; the app then shows the login screen. */
    fun deleteAccount() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                accountRepository.deleteAccount()
            } catch (e: ApiException) {
                _uiState.update { it.copy(isDeleting = false, message = e.toUiText()) }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun load() {
        viewModelScope.launch {
            try {
                val user = accountRepository.profile()
                _uiState.update { it.withUser(user).copy(isLoading = false) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isLoading = false, loadError = e.toUiText()) }
            }
        }
    }

    private fun ProfileUiState.withUser(user: User) =
        copy(user = user, name = user.name, iban = user.iban?.let(::formatIban).orEmpty())
}
