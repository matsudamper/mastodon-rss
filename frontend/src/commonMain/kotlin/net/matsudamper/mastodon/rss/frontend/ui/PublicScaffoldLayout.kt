package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun PublicScaffold(
    onClickHome: () -> Unit,
    onClickAdmin: () -> Unit,
    snackbarHostState: SnackbarHostState = rememberSnackbarHostState(),
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    AppScaffoldLayout(
        snackbarHostState = snackbarHostState,
        topBar = {
            PublicTopAppBar(
                onClickHome = onClickHome,
                onClickAdmin = onClickAdmin,
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicTopAppBar(
    onClickHome: () -> Unit,
    onClickAdmin: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = "mastodon-rss",
                        modifier = Modifier.clickable(onClick = onClickHome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = onClickAdmin) {
                        Text("管理画面")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
