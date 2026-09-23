package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 画像を枠いっぱいに切り抜いて出す。押したりポインタを載せたりすると下の部品に届く。
 *
 * Web では HTML の img を埋め込む。canvas に描く形だと画像のバイト列を読む必要があり、
 * 相手のサーバーが CORS を許していない画像は出せない。
 *
 * @param layer Web ではこれより上のダイアログやメニューが開いている間は隠す
 * @param highlighted 押している間とポインタが載っている間は true。Web では img が canvas の上に
 *   重なって Compose のリップルが隠れるので、代わりに画像を暗くする
 */
@Composable
internal expect fun HtmlImage(
    layer: HtmlImageLayer,
    url: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
)
