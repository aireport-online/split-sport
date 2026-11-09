package online.aireport.split.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import online.aireport.split.R
import online.aireport.split.data.model.Member

/** A member's name, with "(jij)" after it for the signed-in person: "Anna (jij)". */
@Composable
@ReadOnlyComposable
fun memberLabel(member: Member, myMemberId: String): String =
    if (member.id == myMemberId) stringResource(R.string.member_you, member.name) else member.name
