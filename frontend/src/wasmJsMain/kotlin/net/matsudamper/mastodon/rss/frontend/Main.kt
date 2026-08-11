package net.matsudamper.mastodon.rss.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.browser.document
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigator
import net.matsudamper.mastodon.rss.frontend.screen.HomeScreen
import net.matsudamper.mastodon.rss.frontend.screen.NotFoundScreen
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreen
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
        val navigator = rememberNavigator()

        NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.back() },
            entryProvider =
            entryProvider {
                entry<Screen.Home> {
                    HomeScreen(onNavigate = navigator::navigateTo)
                }
                entry<Screen.Admin> {
                    AdminScreen(onNavigate = navigator::navigateTo)
                }
                entry<Screen.Account> { screen ->
                    AccountScreen(
                        username = screen.username,
                        onNavigate = navigator::navigateTo,
                    )
                }
                entry<Screen.NotFound> { screen ->
                    NotFoundScreen(
                        requestedPath = screen.path,
                        onNavigate = navigator::navigateTo,
                    )
                }
            },
        )
    }
}
