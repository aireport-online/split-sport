package online.aireport.split.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.ConfirmDialog
import online.aireport.split.ui.components.ErrorState
import online.aireport.split.ui.components.LoadingState
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.MessageEffect
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.SplitTopBar

private const val MAX_IBAN_LENGTH = 42

@Composable
fun ProfileRoute(onBack: () -> Unit) {
    val viewModel = splitViewModel { ProfileViewModel(it.accountRepository) }
    val state by viewModel.uiState.collectAsUiState()
    ProfileScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onNameChange = viewModel::onNameChange,
        onIbanChange = viewModel::onIbanChange,
        onSave = viewModel::save,
        onLogout = viewModel::logout,
        onDeleteAccount = viewModel::deleteAccount,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNameChange: (String) -> Unit,
    onIbanChange: (String) -> Unit,
    onSave: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, onMessageShown)

    Scaffold(
        topBar = { SplitTopBar(title = stringResource(R.string.profile_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val user = state.user
        when {
            user != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val enabled = !state.isBusy
                SplitCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SplitTextField(
                            value = state.name,
                            onValueChange = { onNameChange(it.take(MAX_NAME_LENGTH)) },
                            label = stringResource(R.string.field_name),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        SplitTextField(
                            value = user.email,
                            onValueChange = {},
                            label = stringResource(R.string.field_email),
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            supportingText = stringResource(R.string.profile_email_hint),
                        )
                        SplitTextField(
                            value = state.iban,
                            onValueChange = { onIbanChange(it.take(MAX_IBAN_LENGTH)) },
                            label = stringResource(R.string.field_iban),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            supportingText = stringResource(R.string.profile_iban_hint),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        PrimaryButton(
                            text = stringResource(R.string.action_save),
                            onClick = onSave,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.canSave,
                            loading = state.isSaving,
                        )
                    }
                }
                SecondaryButton(
                    text = stringResource(R.string.profile_logout),
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    loading = state.isLoggingOut,
                )
                SecondaryButton(
                    text = stringResource(R.string.profile_delete),
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    loading = state.isDeleting,
                    destructive = true,
                )
            }
            state.loadError != null -> ErrorState(state.loadError.resolve(), onRetry, Modifier.padding(padding))
            else -> LoadingState(Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.profile_delete_title),
            text = stringResource(R.string.profile_delete_text),
            confirmLabel = stringResource(R.string.profile_delete),
            onConfirm = {
                confirmDelete = false
                onDeleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
