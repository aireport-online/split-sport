package online.aireport.split.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.core.DEFAULT_RATE_CENTS_PER_KM
import online.aireport.split.core.formatAmountInput
import online.aireport.split.core.formatCents
import online.aireport.split.core.formatKilometres
import online.aireport.split.core.parseAmount
import online.aireport.split.core.tripCostCents
import online.aireport.split.ui.components.AmountField
import online.aireport.split.ui.components.LinkButton
import online.aireport.split.ui.components.PaperDialog
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.theme.SplitTheme

private const val MAX_KILOMETRES_LENGTH = 7

/**
 * "Rit berekenen": opens a helper that turns kilometres and a rate per kilometre into the amount
 * of the expense. [onTrip] gets the cost and the description for the ride, "Rit (84 km)".
 */
@Composable
fun TripButton(enabled: Boolean, onTrip: (costCents: Long, description: String) -> Unit, modifier: Modifier = Modifier) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    LinkButton(stringResource(R.string.trip_calculate), onClick = { dialogOpen = true }, modifier = modifier, enabled = enabled)
    if (dialogOpen) {
        TripDialog(
            onConfirm = { costCents, description ->
                dialogOpen = false
                onTrip(costCents, description)
            },
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun TripDialog(onConfirm: (costCents: Long, description: String) -> Unit, onDismiss: () -> Unit) {
    var kilometres by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf(formatAmountInput(DEFAULT_RATE_CENTS_PER_KM)) }
    val rateCents = parseAmount(rate)
    val costCents = rateCents?.let { tripCostCents(kilometres, it) }
    val distance = formatKilometres(kilometres)
    val description = stringResource(R.string.trip_description, distance.orEmpty())

    PaperDialog(
        title = stringResource(R.string.trip_calculate),
        confirmLabel = stringResource(R.string.trip_fill_in),
        onConfirm = { if (costCents != null) onConfirm(costCents, description) },
        onDismiss = onDismiss,
        confirmEnabled = costCents != null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SplitTextField(
                value = kilometres,
                onValueChange = { typed ->
                    kilometres = typed.filter { it.isDigit() || it == ',' || it == '.' }.take(MAX_KILOMETRES_LENGTH)
                },
                label = stringResource(R.string.trip_kilometres),
                modifier = Modifier.fillMaxWidth(),
                isError = kilometres.isNotBlank() && distance == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            AmountField(
                value = rate,
                onValueChange = { rate = it },
                label = stringResource(R.string.trip_rate),
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Done,
            )
            if (costCents != null && rateCents != null) {
                Text(
                    text = stringResource(R.string.trip_result, distance.orEmpty(), formatCents(rateCents), formatCents(costCents)),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyLarge,
                    color = SplitTheme.colors.ink,
                )
            }
        }
    }
}
