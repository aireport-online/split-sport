package online.aireport.split.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.data.model.Member
import online.aireport.split.ui.theme.SplitTheme

/**
 * Picks one member of the group: the label with the chosen person's highlighted name on one
 * line; tapping it opens a menu with everyone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberPicker(
    label: String,
    members: List<Member>,
    selectedId: String,
    onSelect: (String) -> Unit,
    myMemberId: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SplitTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val selectedPosition = members.indexOfFirst { it.id == selectedId }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .heightIn(min = 48.dp)
                .fieldUnderline(colors.perforation, solid = colors.ink.takeIf { expanded })
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The name moves under the label when the two do not fit on one line.
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (selectedPosition >= 0) {
                    PersonLabel(members[selectedPosition], selectedPosition, myMemberId)
                }
            }
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.small,
            containerColor = colors.paper,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.5.dp, colors.ink),
        ) {
            members.forEachIndexed { position, member ->
                DropdownMenuItem(
                    text = { PersonLabel(member, position, myMemberId) },
                    onClick = {
                        onSelect(member.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A person's highlighted name, with "(jij)" after it for the signed-in member (under it when
 * there is no room beside it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonLabel(member: Member, position: Int, myMemberId: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PersonName(member.name, position)
        if (member.id == myMemberId) {
            Text(
                text = stringResource(R.string.member_you_suffix),
                style = MaterialTheme.typography.bodyMedium,
                color = SplitTheme.colors.mutedInk,
                maxLines = 1,
            )
        }
    }
}
