package online.aireport.split.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.aireport.split.R
import online.aireport.split.ui.theme.SplitTheme

/** The bar of a screen you arrive at: a back chevron and the title, centred, on the desk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val colors = SplitTheme.colors
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconActionButton(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.action_back), onBack)
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.desk,
            scrolledContainerColor = colors.desk,
            titleContentColor = colors.ink,
        ),
    )
}

/** The bar of the home screen: the screen title, large and at the start. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val colors = SplitTheme.colors
    TopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.desk,
            scrolledContainerColor = colors.desk,
            titleContentColor = colors.ink,
        ),
    )
}

/** The screen's main action, fixed at the bottom of the screen above the navigation bar. */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SplitTheme.colors.desk)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
