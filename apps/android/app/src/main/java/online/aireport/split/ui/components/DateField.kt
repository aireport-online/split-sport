package online.aireport.split.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import online.aireport.split.R
import online.aireport.split.core.formatLongDate
import online.aireport.split.ui.theme.SplitTheme

/** Shows a calendar date like a field; tapping it opens the date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SplitTheme.colors
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button) { pickerOpen = true },
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.mutedInk)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .fieldUnderline(colors.perforation, solid = null)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatLongDate(date), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, Modifier.size(20.dp))
        }
    }

    if (pickerOpen) {
        // The picker works in UTC milliseconds; a calendar date is midnight UTC of that day.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                LinkButton(
                    text = stringResource(R.string.action_ok),
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        pickerOpen = false
                    },
                )
            },
            dismissButton = {
                LinkButton(stringResource(R.string.action_cancel), onClick = { pickerOpen = false })
            },
            tonalElevation = 0.dp,
        ) {
            DatePicker(state = pickerState)
        }
    }
}
