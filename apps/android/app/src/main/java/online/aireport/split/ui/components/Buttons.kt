package online.aireport.split.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val MinHeight = 48.dp

/** The primary action of a screen. While [loading] it shows a spinner and ignores taps. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
    ) {
        ButtonLabel(text, loading)
    }
}

/** A secondary action; with [destructive] it is drawn in the error colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = if (destructive) {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        colors = colors,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        ButtonLabel(text, loading)
    }
}

/** A low-emphasis action inside a card, dialog or row. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = if (destructive) {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.textButtonColors()
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = colors,
    ) {
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowScope.ButtonLabel(text: String, loading: Boolean) {
    // The label keeps its place while loading so the button does not change size.
    Box(contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.alpha(if (loading) 0f else 1f), textAlign = TextAlign.Center)
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        }
    }
}
