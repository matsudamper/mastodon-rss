package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * URL の画像を読む。読み終わるまでと、読めなかった場合は null。
 *
 * 画像が無い前提で組み立てられる画面にしておくこと。配信元の画像は
 * 消えることも、描けない形式であることもある。
 */
@Composable
internal fun rememberRemoteImage(url: String?): ImageBitmap? {
    var image: ImageBitmap? by remember(url) { mutableStateOf(null) }

    LaunchedEffect(url) {
        image = if (url == null) null else loadRemoteImage(url)
    }

    return image
}

/**
 * 取得と復号はプラットフォームの API に触るので分ける。
 *
 * 失敗は null で返す。画像が読めないだけで画面を落とさない
 */
internal expect suspend fun loadRemoteImage(url: String): ImageBitmap?
