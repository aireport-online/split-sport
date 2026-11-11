package online.aireport.split.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.GroupDetail
import online.aireport.split.data.model.Sport
import online.aireport.split.ui.common.labelRes
import online.aireport.split.ui.components.HighlighterPicker
import online.aireport.split.ui.components.MAX_NAME_LENGTH
import online.aireport.split.ui.components.MemberPicker
import online.aireport.split.ui.components.OptionPicker
import online.aireport.split.ui.components.Receipt
import online.aireport.split.ui.components.ReceiptPadding
import online.aireport.split.ui.components.SecondaryButton
import online.aireport.split.ui.components.SplitTextField
import online.aireport.split.ui.theme.SplitTheme

private const val MAX_CLUB_LENGTH = 60
private const val MAX_SEASON_LENGTH = 20

/**
 * The team itself. Name, club and season are typed and saved together; the sport, the
 * highlighter and the penningmeester are picked and saved at once.
 */
@Composable
internal fun TeamReceipt(group: GroupDetail, enabled: Boolean, actions: GroupSettingsActions) {
    var name by rememberSaveable(group.name) { mutableStateOf(group.name) }
    var club by rememberSaveable(group.club) { mutableStateOf(group.club.orEmpty()) }
    var season by rememberSaveable(group.season) { mutableStateOf(group.season.orEmpty()) }
    val changed = name.trim() != group.name || club.trim() != group.club.orEmpty() || season.trim() != group.season.orEmpty()
    val canSave = enabled && changed && name.isNotBlank()
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)

    Receipt(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ReceiptPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(stringResource(R.string.settings_team))
            SplitTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = stringResource(R.string.field_name),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                keyboardOptions = words,
            )
            OptionPicker(
                label = stringResource(R.string.field_sport),
                options = Sport.entries,
                selected = group.sport,
                onSelect = actions::changeSport,
                optionLabel = { stringResource(it.labelRes) },
                enabled = enabled,
            )
            SplitTextField(
                value = club,
                onValueChange = { club = it.take(MAX_CLUB_LENGTH) },
                label = stringResource(R.string.field_club),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                keyboardOptions = words,
            )
            SplitTextField(
                value = season,
                onValueChange = { season = it.take(MAX_SEASON_LENGTH) },
                label = stringResource(R.string.field_season),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            if (canSave) {
                SecondaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { actions.updateDetails(name, club, season) },
                    small = true,
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.settings_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SplitTheme.colors.mutedInk,
                )
                HighlighterPicker(selected = group.color, onSelect = actions::recolor, enabled = enabled)
            }
            // The pot's money is kept by a person, never by the pot itself.
            MemberPicker(
                label = stringResource(R.string.treasurer),
                members = group.players,
                selectedId = group.treasurerMemberId.orEmpty(),
                onSelect = actions::changeTreasurer,
                myMemberId = group.myMemberId,
                enabled = enabled,
            )
            Text(
                text = stringResource(R.string.treasurer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SplitTheme.colors.mutedInk,
            )
        }
    }
}
