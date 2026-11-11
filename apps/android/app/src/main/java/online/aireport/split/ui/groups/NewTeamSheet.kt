package online.aireport.split.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import online.aireport.split.R
import online.aireport.split.core.seasonOf
import online.aireport.split.data.NewTeam
import online.aireport.split.data.model.GroupColor
import online.aireport.split.data.model.Sport
import online.aireport.split.ui.common.labelRes
import online.aireport.split.ui.components.HighlighterPicker
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.OptionPicker
import online.aireport.split.ui.components.PrimaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.theme.SplitTheme

private const val MAX_CLUB_LENGTH = 60
private const val MAX_SEASON_LENGTH = 20

/** "Nieuw team" as a sheet of paper from the bottom of the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTeamSheet(onCreate: (NewTeam) -> Unit, onDismiss: () -> Unit) {
    val colors = SplitTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
        containerColor = colors.paper,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
    ) {
        NewTeamForm(
            onCreate = onCreate,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

/**
 * Asks what a new team needs: its name, sport, club (optional), season (prefilled with the
 * running one, July to June) and highlighter.
 */
@Composable
fun NewTeamForm(onCreate: (NewTeam) -> Unit, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    var name by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf(Sport.entries.first()) }
    var club by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf(seasonOf(today)) }
    var color by rememberSaveable { mutableStateOf(GroupColor.entries.first()) }
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.groups_new),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        SplitTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            label = stringResource(R.string.field_name),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.groups_new_placeholder),
            keyboardOptions = words,
        )
        OptionPicker(
            label = stringResource(R.string.field_sport),
            options = Sport.entries,
            selected = sport,
            onSelect = { sport = it },
            optionLabel = { stringResource(it.labelRes) },
        )
        SplitTextField(
            value = club,
            onValueChange = { club = it.take(MAX_CLUB_LENGTH) },
            label = stringResource(R.string.field_club),
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.groups_new_club_placeholder),
            supportingText = stringResource(R.string.field_optional),
            keyboardOptions = words,
        )
        SplitTextField(
            value = season,
            onValueChange = { season = it.take(MAX_SEASON_LENGTH) },
            label = stringResource(R.string.field_season),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Column {
            Text(
                text = stringResource(R.string.settings_color),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
            )
            HighlighterPicker(selected = color, onSelect = { color = it })
        }
        PrimaryButton(
            text = stringResource(R.string.action_create),
            onClick = { onCreate(NewTeam(name, color, sport, club, season)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
    }
}
