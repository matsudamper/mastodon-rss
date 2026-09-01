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

/**
 * 管理画面用の枠。タイトル末尾に「管理画面」を付けた TopAppBar を出す。
 */
@Composable
internal fun AdminScaffold(
    title: String?,
    listener: AdminScaffoldListener,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    AppScaffoldLayout(
        topBar = { AdminTopAppBar(title = title, listener = listener) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopAppBar(
    title: String?,
    listener: AdminScaffoldListener,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = "管理画面".plus(if (title != null) "/$title" else ""),
                        modifier = Modifier.clickable(onClick = listener::onClickAdmin),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = listener::onClickHome) {
                        Text("トップ")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
