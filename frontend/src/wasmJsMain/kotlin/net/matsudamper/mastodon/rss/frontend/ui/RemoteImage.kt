package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.CancellationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes

/**
 * 画像を取ってきて復号する。
 *
 * canvas に描くので、ブラウザの `<img>` のようには読めない。バイト列で受け取って
 * [decodeToImageBitmap] に渡す。SVG や ICO のように復号できない形式は null になる。
 */
internal actual suspend fun loadRemoteImage(url: String): ImageBitmap? {
    return HttpClient(Js).use { client ->
        runCatching { client.get(url).readRawBytes().decodeToImageBitmap() }
            .getOrElse { error ->
                // 画面から離れた合図まで握り潰すと、閉じた画面に描き込むことになる
                if (error is CancellationException) throw error
                null
            }
    }
}
