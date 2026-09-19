package net.matsudamper.mastodon.rss.frontend

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.browser.document
import kotlinx.browser.window
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.TransparentScreen
import net.matsudamper.mastodon.rss.frontend.navigation.TransparentScreenSceneStrategy
import net.matsudamper.mastodon.rss.frontend.navigation.WasmNavigator
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavController
import net.matsudamper.mastodon.rss.frontend.screen.NotFoundScreen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountFollowersScreen
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountNoteScreen
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountFeedNewScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountProfileEditScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreen
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreen
import net.matsudamper.mastodon.rss.frontend.ui.AppTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        Box(Modifier.fallbackToWindowSize()) {
            App()
        }
    }
}

/**
 * 大きさが無制限で測られたときに、ウィンドウの大きさで測り直す。
 *
 * Compose の Web 実装はキャンバスの大きさが決まる前に最初の測定が走ることがあり、
 * そのとき画面全体が無制限の制約で測られる。無制限をそのまま受けると、
 * 制約の最大値を自分の大きさにする部品（Material3 の TopAppBar など）が
 * Compose の扱える大きさ（1 辺 16777215）を超えて落ちる。
 *
 * 大きさが決まっているときは何もしない。
 */
private fun Modifier.fallbackToWindowSize(): Modifier = layout { measurable, constraints ->
    val fallback = constraints.copy(
        maxWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            (window.innerWidth * density).toInt()
        },
        maxHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            (window.innerHeight * density).toInt()
        },
    )

    val placeable = measurable.measure(fallback)
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}

/**
 * 画面の入口。URL に対応する画面を 1 つ出す。
 *
 * どのパスでも管理画面を出していたのをやめ、[Screen] の判定に通す。
 * 遷移は Navigation 3 の [NavDisplay] に任せ、バックスタックは
 * ブラウザの履歴に合わせたものを渡す。
 */
@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { HttpClient(Js) })) }
            .build()
    }

    AppTheme {
        val platformNavController = rememberNavController()
        val navController: Navigator = remember(platformNavController) {
            WasmNavigator(platformNavController)
        }

        NavDisplay(
            backStack = platformNavController.backStack,
            onBack = { platformNavController.back() },
            sceneStrategies = listOf(TransparentScreenSceneStrategy()),
            entryProvider =
            entryProvider {
                entry<Screen.Home> {
                    HomeScreen(navController = navController)
                }
                entry<Screen.Admin> {
                    AdminScreen(
                        platform = WasmScreenPlatform,
                        navController = navController,
                    )
                }
                entry<Screen.AdminAccounts> {
                    AdminAccountsScreen(navController = navController)
                }
                entry<Screen.AdminAccountNew> {
                    AdminAccountNewScreen(navController = navController)
                }
                entry<Screen.AdminAccount> { screen ->
                    AdminAccountScreen(
                        username = screen.username,
                        platform = WasmScreenPlatform,
                        navController = navController,
                    )
                }
                entry<Screen.AdminAccountFeedNew>(
                    metadata = TransparentScreen.asMetadata(),
                ) { screen ->
                    AdminAccountFeedNewScreen(
                        username = screen.username,
                        navController = navController,
                    )
                }
                entry<Screen.AdminAccountProfileEdit>(metadata = TransparentScreen.asMetadata()) { screen ->
                    AdminAccountProfileEditScreen(username = screen.username, navController = navController)
                }
                entry<Screen.Account> { screen ->
                    AccountScreen(
                        username = screen.username,
                        platform = WasmScreenPlatform,
                        navController = navController,
                    )
                }
                entry<Screen.AccountNote>(
                    metadata = TransparentScreen.asMetadata(),
                ) { screen ->
                    AccountNoteScreen(
                        username = screen.username,
                        noteId = screen.noteId,
                        platform = WasmScreenPlatform,
                        navController = navController,
                    )
                }
                entry<Screen.AccountFollowers>(
                    metadata = TransparentScreen.asMetadata(),
                ) { screen ->
                    AccountFollowersScreen(
                        username = screen.username,
                        platform = WasmScreenPlatform,
                        navController = navController,
                    )
                }
                entry<Screen.NotFound> { screen ->
                    NotFoundScreen(
                        requestedPath = screen.path,
                        navController = navController,
                    )
                }
            },
        )
    }
}

private object WasmScreenPlatform : ScreenPlatform {
    override val host: String
        get() = window.location.host

    override fun openExternalLink(url: String) {
        net.matsudamper.mastodon.rss.frontend.ui.openExternalLink(url)
    }

    override fun copyToClipboard(text: String, onResult: (Boolean) -> Unit) {
        net.matsudamper.mastodon.rss.frontend.ui.copyToClipboard(text, onResult)
    }
}
