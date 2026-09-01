package net.matsudamper.mastodon.rss.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.browser.document
import kotlinx.browser.window
import net.matsudamper.mastodon.rss.frontend.navigation.CollectNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.LocalNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.WasmNavigatorReceiver
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigationEvents
import net.matsudamper.mastodon.rss.frontend.navigation.rememberNavigator
import net.matsudamper.mastodon.rss.frontend.screen.NotFoundScreen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.account.AccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreen
import net.matsudamper.mastodon.rss.frontend.screen.admin.AdminScreenUiState
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
        val navigator = rememberNavigator()
        val navigationEvents = rememberNavigationEvents()
        CollectNavigationEvents(
            events = navigationEvents,
            receiver = WasmNavigatorReceiver(navigator),
        )

        CompositionLocalProvider(LocalNavigationEvents provides navigationEvents) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.back() },
                entryProvider =
                entryProvider {
                    entry<Screen.Home> {
                        HomeScreen()
                    }
                    entry<Screen.Admin> {
                        AdminScreen(platform = WasmScreenPlatform)
                    }
                    entry<Screen.AdminAccounts> {
                        AdminAccountsScreen()
                    }
                    entry<Screen.AdminAccountNew> {
                        AdminAccountNewScreen()
                    }
                    entry<Screen.AdminAccount> { screen ->
                        AdminAccountScreen(
                            username = screen.username,
                            platform = WasmScreenPlatform,
                        )
                    }
                    entry<Screen.Account> { screen ->
                        AccountScreen(
                            username = screen.username,
                            platform = WasmScreenPlatform,
                        )
                    }
                    entry<Screen.NotFound> { screen ->
                        NotFoundScreen(requestedPath = screen.path)
                    }
                },
            )
        }
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

    @Composable
    override fun NoteContent(contentHtml: String, modifier: Modifier) {
        net.matsudamper.mastodon.rss.frontend.ui.NoteContent(contentHtml, modifier)
    }

    @Composable
    override fun AdminLoginPasswordField(
        content: AdminScreenUiState.Content.Login,
        listener: AdminScreenUiState.Listener,
    ) {
        net.matsudamper.mastodon.rss.frontend.ui.AdminLoginPasswordField(
            password = content.password,
            onPasswordChange = listener::onPasswordChanged,
            onSubmit = listener::onClickLogin,
            enabled = content.inputEnabled && !content.submitting,
            hasError = content.error != null,
        )
    }
}
