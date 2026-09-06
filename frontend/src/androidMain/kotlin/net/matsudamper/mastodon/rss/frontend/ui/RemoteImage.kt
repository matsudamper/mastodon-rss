package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Android 側はプレビューを出すためだけにあるので、通信はしない。
 */
internal actual suspend fun loadRemoteImage(url: String): ImageBitmap? = null
