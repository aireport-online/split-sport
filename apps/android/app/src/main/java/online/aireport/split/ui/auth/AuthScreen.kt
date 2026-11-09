package online.aireport.split.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.aireport.split.R
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.ErrorText
import online.aireport.split.ui.components.LinkButton
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.components.Wordmark

@Composable
fun AuthRoute() {
    val viewModel = splitViewModel { AuthViewModel(it.accountRepository) }
    val state by viewModel.uiState.collectAsUiState()
    AuthScreen(
        state = state,
        onModeChange = viewModel::onModeChange,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onServerUrlChange = viewModel::onServerUrlChange,
        onToggleAdvanced = viewModel::onToggleAdvanced,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onToggleAdvanced: () -> Unit,
    onSubmit: () -> Unit,
) {
    val isRegistering = state.mode == AuthMode.REGISTER
    val enabled = !state.isSubmitting
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 40.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(fontSize = 84.sp)
            Spacer(Modifier.height(36.dp))

            Receipt(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(ReceiptPadding),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (isRegistering) {
                        SplitTextField(
                            value = state.name,
                            onValueChange = { onNameChange(it.take(MAX_NAME_LENGTH)) },
                            label = stringResource(R.string.field_name),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.PersonFullName },
                            enabled = enabled,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
                    SplitTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(R.string.field_email_address),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.EmailAddress },
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    SplitTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = stringResource(R.string.field_password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentType = if (isRegistering) ContentType.NewPassword else ContentType.Password
                            },
                        enabled = enabled,
                        supportingText = if (isRegistering) stringResource(R.string.auth_password_hint) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    AnimatedVisibility(visible = state.advancedOpen) {
                        SplitTextField(
                            value = state.serverUrl,
                            onValueChange = onServerUrlChange,
                            label = stringResource(R.string.field_server),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            placeholder = stringResource(R.string.auth_server_placeholder),
                            supportingText = stringResource(R.string.auth_server_hint),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }
                    if (state.error != null) ErrorText(state.error.resolve())
                }
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = stringResource(if (isRegistering) R.string.auth_register else R.string.auth_login),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
                loading = state.isSubmitting,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (isRegistering) R.string.auth_have_account else R.string.auth_no_account),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinkButton(
                    text = stringResource(if (isRegistering) R.string.auth_login else R.string.auth_register),
                    onClick = { onModeChange(if (isRegistering) AuthMode.LOGIN else AuthMode.REGISTER) },
                    enabled = enabled,
                )
            }
            LinkButton(stringResource(R.string.auth_advanced), onToggleAdvanced)
        }
    }
}
