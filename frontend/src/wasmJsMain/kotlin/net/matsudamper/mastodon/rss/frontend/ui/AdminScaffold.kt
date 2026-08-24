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
 * 管理画面用の枠。タイトル末尾に「管理画面」を付けた TopAppBar を出す。
 *
 * @param title 管理画面トップなら [ADMIN_ROOT_TITLE]、それ以外は付ける前の見出し
 */
@Composable
fun AdminScaffold(
    title: String,
    onNavigate: (Screen) -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    AppScaffoldLayout(
        topBar = {
            AdminTopAppBar(
                title = title,
                onNavigate = onNavigate,
            )
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopAppBar(
    title: String,
    onNavigate: (Screen) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = "管理画面/$title",
                        modifier = Modifier.clickable { onNavigate(Screen.Admin) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = { onNavigate(Screen.Home) }) {
                        Text("トップ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            HorizontalDivider(color = dividerColor())
        }
    }
}
