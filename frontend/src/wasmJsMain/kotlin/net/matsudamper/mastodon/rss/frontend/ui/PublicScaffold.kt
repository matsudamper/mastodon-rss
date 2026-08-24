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
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

/**
 * 公開画面用の枠。サイト名と管理画面への導線を TopAppBar に出す。
 */
@Composable
fun PublicScaffold(
    onNavigate: (Screen) -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    AppScaffoldLayout(
        topBar = { PublicTopAppBar(onNavigate = onNavigate) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicTopAppBar(onNavigate: (Screen) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = Screen.SITE_NAME,
                        modifier = Modifier.clickable { onNavigate(Screen.Home) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = { onNavigate(Screen.Admin) }) {
                        Text("管理画面")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            HorizontalDivider(color = dividerColor())
        }
    }
}
