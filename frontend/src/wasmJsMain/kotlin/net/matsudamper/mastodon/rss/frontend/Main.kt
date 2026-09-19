package net.matsudamper.mastodon.rss.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
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
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body!!
    if (canUseWebGl2().not()) {
        body.appendChild(createWebGl2UnavailableNotice())
        return
    }

    ComposeViewport(body) {
        App()
    }
}

/**
 * WebGL2 でコンテキストを作れるかを調べる。
 *
 * 描画は Skia を WebGL2 の上で動かしていて、WebGL1 にもソフトウェア描画にも
 * 代替経路が無い。使えない状態で起動すると描画の初期化が例外になり、
 * 何も出ないまま終わる。
 *
 * ブラウザが WebGL2 に対応していても、GPU がブロックリストに掛かっていたり
 * リモートデスクトップ越しで仮想ディスプレイアダプターになっていると作れない。
 * 対応の有無ではなく実際に作って確かめる。
 */
private fun canUseWebGl2(): Boolean {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    return canvas.getContext("webgl2") != null
}

private fun createWebGl2UnavailableNotice(): HTMLElement {
    val notice = document.createElement("div") as HTMLElement
    notice.setAttribute(
        "style",
        "display:flex; flex-direction:column; gap:8px; align-items:center; justify-content:center;" +
            " height:100%; padding:16px; box-sizing:border-box; text-align:center;" +
            " font-family:sans-serif; color:#1b1b1b; background:#fdfdfd;",
    )

    val title = document.createElement("div") as HTMLElement
    title.setAttribute("style", "font-size:18px; font-weight:bold;")
    title.textContent = "画面を表示できません"

    val description = document.createElement("div") as HTMLElement
    description.setAttribute("style", "font-size:14px; line-height:1.6; max-width:32em;")
    description.textContent = "このブラウザーで WebGL2 が使えないため、描画を開始できませんでした。" +
        "ブラウザーを再起動すると直ることがあります。" +
        "直らない場合は、ハードウェアアクセラレーションを有効にするか、別のブラウザーでお試しください。"

    notice.appendChild(title)
    notice.appendChild(description)
    return notice
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
