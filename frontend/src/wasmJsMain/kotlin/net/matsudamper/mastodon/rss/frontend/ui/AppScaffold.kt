package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

/**
 * どの画面にも共通の枠。ヘッダー、スクロール、横幅の上限をここでまとめる。
 *
 * 画面の中身には、広い画面かどうかだけを渡す。判定の基準（[WideBreakpoint]）を
 * 画面ごとに持つと、同じ幅なのに片方だけ 2 カラムになる、といったずれ方をする。
 *
 * @param content 中身。`wide` が true なら 2 カラムに耐える幅がある
 */
@Composable
fun AppScaffold(
    onNavigate: (Screen) -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppHeader(onNavigate = onNavigate)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= WideBreakpoint
            val outerPadding = if (wide) 24.dp else 12.dp

            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = outerPadding, vertical = outerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier =
                    Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = { content(wide) },
                )
            }
        }
    }
}

/**
 * 画面の一番上に出す帯。どの画面からでもトップと管理画面に行けるようにする。
 */
@Composable
private fun AppHeader(onNavigate: (Screen) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = Screen.SITE_NAME,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextLink(
                    text = "トップ",
                    onClick = { onNavigate(Screen.Home) },
                )
                TextLink(
                    text = "管理画面",
                    onClick = { onNavigate(Screen.Admin) },
                )
            }
            HorizontalDivider(color = dividerColor())
        }
    }
}
