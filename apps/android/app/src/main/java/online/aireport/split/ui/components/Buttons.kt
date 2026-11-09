package online.aireport.split.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import online.aireport.split.ui.theme.SplitTheme

private val ButtonHeight = 48.dp
private val SmallButtonHeight = 36.dp
private val OutlineWidth = 1.5.dp
internal const val DISABLED_ALPHA = 0.45f

/** The primary action: ink fill, 48 dp high. While [loading] it shows a spinner and ignores taps. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = SplitTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.onInk,
            // A loading button keeps its ink; only a button that cannot be used fades.
            disabledContainerColor = colors.ink.copy(alpha = if (loading) 1f else DISABLED_ALPHA),
            disabledContentColor = colors.onInk,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        ButtonLabel(text, loading)
    }
}

/**
 * A secondary action: a 1.5 dp ink outline; with [destructive] in stamp red. [small] is the
 * 36 dp variant for use inside a receipt (its touch target stays 48 dp).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
    small: Boolean = false,
) {
    val colors = SplitTheme.colors
    val color = if (destructive) colors.stamp else colors.ink
    val active = enabled && !loading
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (small) SmallButtonHeight else ButtonHeight),
        enabled = active,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = color.copy(alpha = DISABLED_ALPHA),
        ),
        border = BorderStroke(OutlineWidth, if (active) color else color.copy(alpha = DISABLED_ALPHA)),
        contentPadding = PaddingValues(horizontal = if (small) 12.dp else 18.dp, vertical = 6.dp),
    ) {
        ButtonLabel(text, loading, small)
    }
}

/** A low-emphasis action: underlined text; with [destructive] in stamp red. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = SplitTheme.colors
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = ButtonHeight, minHeight = ButtonHeight)
            .padding(horizontal = 8.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (destructive) colors.stamp else colors.ink,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
    }
}

/** A square button with only an icon, e.g. the steppers and "remove this line". */
@Composable
fun IconActionButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val colors = SplitTheme.colors
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (outlined) {
                    Modifier.border(BorderStroke(OutlineWidth, colors.ink.copy(alpha = alpha)), MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .size(if (outlined) SmallButtonHeight else ButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = colors.ink.copy(alpha = alpha))
    }
}

@Composable
private fun ButtonLabel(text: String, loading: Boolean, small: Boolean = false) {
    // The label keeps its place while loading so the button does not change size.
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
    }
}
