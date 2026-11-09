package online.aireport.split.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.aireport.split.core.parseAmount
import online.aireport.split.ui.theme.FigureTextStyle
import online.aireport.split.ui.theme.SplitTheme

/**
 * The line under a field: a dashed perforation that turns into solid ink when the field has
 * focus, and into stamp red when its value is wrong.
 */
internal fun Modifier.fieldUnderline(perforation: Color, solid: Color?): Modifier = drawBehind {
    val y = size.height - PerforationWidth.toPx() / 2
    if (solid != null) {
        drawLine(solid, Offset(0f, y), Offset(size.width, y), strokeWidth = PerforationWidth.toPx())
    } else {
        drawPerforation(perforation, y)
    }
}

/**
 * A field without a box: a small muted label, the value in mono below it and a perforation
 * underline. With [showLabel] false the label is only announced, for a field in a labelled row.
 */
@Composable
fun SplitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    showLabel: Boolean = true,
    supportingText: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = SplitTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val valueColor = if (enabled && !readOnly) colors.ink else colors.mutedInk
    val underline = when {
        isError -> colors.stamp
        focused -> colors.ink
        else -> null
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            if (!showLabel) contentDescription = label
            if (isError) error(supportingText.orEmpty())
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = valueColor),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.ink),
        decorationBox = { innerTextField ->
            Column {
                if (showLabel) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.mutedInk)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (showLabel) 34.dp else 48.dp)
                        .fieldUnderline(colors.perforation, underline)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prefix != null) {
                        Text(
                            text = "$prefix ",
                            modifier = Modifier.clearAndSetSemantics {},
                            style = textStyle,
                            color = valueColor,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                modifier = Modifier.fillMaxWidth(),
                                style = textStyle,
                                color = colors.mutedInk.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                }
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) colors.stamp else colors.mutedInk,
                    )
                }
            }
        },
    )
}

private const val MAX_AMOUNT_LENGTH = 12

/**
 * A field for an amount in euros. Accepts digits with a comma or a dot and marks itself as
 * wrong when what was typed is not an amount. With [figure] the amount is printed as a headline
 * figure; with [alignEnd] it is right-aligned like the amount column of a receipt.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    figure: Boolean = false,
    alignEnd: Boolean = false,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val baseStyle = if (figure) FigureTextStyle.copy(fontSize = 26.sp, lineHeight = 32.sp) else MaterialTheme.typography.bodyLarge
    SplitTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(typed.filter { it.isDigit() || it == ',' || it == '.' }.take(MAX_AMOUNT_LENGTH))
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = value.isNotBlank() && parseAmount(value) == null,
        showLabel = showLabel,
        placeholder = placeholder,
        prefix = if (alignEnd) null else "€",
        textStyle = if (alignEnd) baseStyle.copy(textAlign = TextAlign.End) else baseStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}
