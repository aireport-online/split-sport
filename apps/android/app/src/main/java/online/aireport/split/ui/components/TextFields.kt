package online.aireport.split.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import online.aireport.split.core.parseAmount

/** A single-line text field in the app's shape. */
@Composable
fun SplitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.small,
    )
}

private const val MAX_AMOUNT_LENGTH = 12

/**
 * A field for an amount in euros. Accepts digits with a comma or a dot and marks itself as
 * wrong when what was typed is not an amount.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    SplitTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(typed.filter { it.isDigit() || it == ',' || it == '.' }.take(MAX_AMOUNT_LENGTH))
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = value.isNotBlank() && parseAmount(value) == null,
        placeholder = placeholder,
        supportingText = supportingText,
        prefix = "€",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}
