package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

/**
 * どの画面にも共通の枠。ヘッダーと横幅の上限をここでまとめる。
 *
 * 画面の中身には、広い画面かどうかだけを渡す。判定の基準（[WideBreakpoint]）を
 * 画面ごとに持つと、同じ幅なのに片方だけ 2 カラムになる、といったずれ方をする。
 *
 * @param screen いま出している画面。ヘッダーの見た目とタイトルに使う
 * @param content 中身。`wide` が true なら 2 カラムに耐える幅がある
 */
@Composable
fun AppScaffold(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            if (screen.isAdmin) {
                AdminTopAppBar(screen = screen, onNavigate = onNavigate)
            } else {
                PublicTopAppBar(onNavigate = onNavigate)
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= WideBreakpoint
                val outerPadding = if (wide) 24.dp else 12.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = outerPadding, vertical = outerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = ContentMaxWidth)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = { content(wide) },
                    )
                }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopAppBar(
    screen: Screen,
    onNavigate: (Screen) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column {
            TopAppBar(
                title = {
                    val navigateTarget = if (screen == Screen.Admin) null else Screen.Admin
                    Text(
                        text = screen.appBarTitle,
                        modifier =
                        if (navigateTarget != null) {
                            Modifier.clickable { onNavigate(navigateTarget) }
                        } else {
                            Modifier
                        },
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
