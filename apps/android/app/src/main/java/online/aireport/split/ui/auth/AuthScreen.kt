package online.aireport.split.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.common.collectAsUiState
import online.aireport.split.ui.common.splitViewModel
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.QuietButton
import online.aireport.split.ui.components.SplitCard
import online.aireport.split.ui.components.SplitTextField

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.auth_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SplitCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModeSwitch(state.mode, onModeChange, enabled = !state.isSubmitting)
                    Spacer(Modifier.height(4.dp))

                    if (isRegistering) {
                        SplitTextField(
                            value = state.name,
                            onValueChange = { onNameChange(it.take(MAX_NAME_LENGTH)) },
                            label = stringResource(R.string.field_name),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentType = ContentType.PersonFullName },
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        )
                    }
                    SplitTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(R.string.field_email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.EmailAddress },
                        enabled = !state.isSubmitting,
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
                        enabled = !state.isSubmitting,
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
                            enabled = !state.isSubmitting,
                            placeholder = stringResource(R.string.auth_server_placeholder),
                            supportingText = stringResource(R.string.auth_server_hint),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error.resolve(),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    PrimaryButton(
                        text = stringResource(if (isRegistering) R.string.auth_register else R.string.auth_login),
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canSubmit,
                        loading = state.isSubmitting,
                    )
                }
            }

            QuietButton(
                text = stringResource(R.string.auth_advanced),
                onClick = onToggleAdvanced,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ModeSwitch(mode: AuthMode, onModeChange: (AuthMode) -> Unit, enabled: Boolean) {
    val labels = listOf(AuthMode.LOGIN to R.string.auth_login, AuthMode.REGISTER to R.string.auth_register)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size, MaterialTheme.shapes.small),
                enabled = enabled,
                icon = {},
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}
