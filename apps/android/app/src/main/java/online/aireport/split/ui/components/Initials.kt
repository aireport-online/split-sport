package online.aireport.split.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.aireport.split.core.initials
import online.aireport.split.data.model.GroupColor
import online.aireport.split.ui.theme.SplitTheme
import online.aireport.split.ui.theme.Tint

/** A group: a rounded tile in the group's tint with its initials. */
@Composable
fun GroupTile(name: String, color: GroupColor, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    InitialsBadge(name, SplitTheme.colors.tints.of(color), RoundedCornerShape(size * 0.28f), size, modifier)
}

/** A person: a circle in the tint of their position in the group with their initials. */
@Composable
fun MemberAvatar(name: String, position: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    InitialsBadge(name, SplitTheme.colors.tints.ofMember(position), CircleShape, size, modifier)
}

@Composable
private fun InitialsBadge(name: String, tint: Tint, shape: Shape, size: Dp, modifier: Modifier) {
    Box(
        // Decorative: the name is always written next to it. The fixed size and font size keep
        // the initials inside the badge at any font scale.
        modifier = modifier
            .size(size)
            .background(tint.background, shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = tint.ink,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp.nonScaled(),
            maxLines = 1,
        )
    }
}
