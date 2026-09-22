package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 画像を枠いっぱいに切り抜いて出す。
 *
 * Web では HTML の img を埋め込む。canvas に描く形だと画像のバイト列を読む必要があり、
 * 相手のサーバーが CORS を許していない画像は出せない。
 */
@Composable
internal expect fun HtmlImage(
    url: String,
    modifier: Modifier = Modifier,
)
