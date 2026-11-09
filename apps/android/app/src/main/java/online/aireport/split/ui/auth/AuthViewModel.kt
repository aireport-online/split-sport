package online.aireport.split.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.aireport.split.data.AccountRepository
import online.aireport.split.data.api.ApiException
import online.aireport.split.ui.common.UiText
import online.aireport.split.ui.common.toUiText

enum class AuthMode { LOGIN, REGISTER }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val serverUrl: String = "",
    val advancedOpen: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && serverUrl.isNotBlank() &&
            (mode == AuthMode.LOGIN || name.isNotBlank())
}

class AuthViewModel(private val accountRepository: AccountRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val serverUrl = accountRepository.session.first().serverUrl
            // A build without a default server has to ask for one.
            _uiState.update { it.copy(serverUrl = serverUrl, advancedOpen = serverUrl.isBlank()) }
        }
    }

    fun onModeChange(mode: AuthMode) = _uiState.update { it.copy(mode = mode, error = null) }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email) }

    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password) }

    fun onServerUrlChange(serverUrl: String) = _uiState.update { it.copy(serverUrl = serverUrl) }

    fun onToggleAdvanced() = _uiState.update { it.copy(advancedOpen = !it.advancedOpen) }

    /** Logs in or registers; on success the session changes and the app leaves this screen. */
    fun submit() {
        val form = _uiState.value
        if (!form.canSubmit || form.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                when (form.mode) {
                    AuthMode.LOGIN -> accountRepository.login(form.serverUrl, form.email, form.password)
                    AuthMode.REGISTER ->
                        accountRepository.register(form.serverUrl, form.name, form.email, form.password)
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(isSubmitting = false, error = e.toUiText()) }
            }
        }
    }
}
