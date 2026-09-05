package net.matsudamper.mastodon.rss.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.browser.document
import kotlinx.browser.window
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.TransparentScreen
import net.matsudamper.mastodon.rss.frontend.navigation.TransparentScreenSceneStrategy
import net.matsudamper.mastodon.rss.frontend.navigation.WasmNavigator
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavController
import net.matsudamper.mastodon.rss.frontend.screen.NotFoundScreen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountFeedNewScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreen
import net.matsudamper.mastodon.rss.frontend.screen.home.HomeScreen
import net.matsudamper.mastodon.rss.frontend.ui.AppTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        App()
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
    AppTheme {
        val platformNavController = rememberNavController()
        val navController: Navigator = remember(platformNavController) {
            WasmNavigator(platformNavController)
        }

        NavDisplay(
            backStack = platformNavController.backStack,
            onBack = { platformNavController.back() },
            // 重なる画面だけ、下の画面を残したまま描く。
            // 当てはまらない画面は NavDisplay の既定（1 画面）に落ちる
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
                entry<Screen.Account> { screen ->
                    AccountScreen(
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
