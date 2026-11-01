package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext

@Composable
internal actual fun rememberLinkPreviewImageLoader(): ImageLoader =
    SingletonImageLoader.get(LocalPlatformContext.current)
