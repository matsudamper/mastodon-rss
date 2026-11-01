package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.js.toJsString
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

@Composable
internal actual fun rememberLinkPreviewImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val client = remember {
        HttpClient(Js) {
            engine {
                configureRequest {
                    credentials = "omit".toJsString()
                }
            }
        }
    }
    val imageLoader = remember(context, client) {
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { client })) }
            .build()
    }
    DisposableEffect(imageLoader, client) {
        onDispose {
            imageLoader.shutdown()
            client.close()
        }
    }
    return imageLoader
}
