package net.matsudamper.mastodon.rss.frontend

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.browser.document
import kotlinx.browser.window
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
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
        val platform = WasmScreenPlatform

        NavDisplay(
            backStack = navigator.backStack,
            onBack = { navigator.back() },
            entryProvider =
            entryProvider {
                entry<Screen.Home> {
                    HomeScreen(
                        onClickAccount = { navigator.navigateTo(Screen.Account(it)) },
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                    )
                }
                entry<Screen.Admin> {
                    AdminScreen(
                        platform = platform,
                        onClickAccounts = { navigator.navigateTo(Screen.AdminAccounts) },
                        onClickNewAccount = { navigator.navigateTo(Screen.AdminAccountNew) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                    )
                }
                entry<Screen.AdminAccounts> {
                    AdminAccountsScreen(
                        onClickNewAccount = { navigator.navigateTo(Screen.AdminAccountNew) },
                        onClickPublicAccount = { navigator.navigateTo(Screen.Account(it)) },
                        onClickAdminAccount = { navigator.navigateTo(Screen.AdminAccount(it)) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                    )
                }
                entry<Screen.AdminAccountNew> {
                    AdminAccountNewScreen(
                        onClickAccounts = { navigator.navigateTo(Screen.AdminAccounts) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                    )
                }
                entry<Screen.AdminAccount> { screen ->
                    AdminAccountScreen(
                        username = screen.username,
                        platform = platform,
                        onClickOpenAccount = { navigator.navigateTo(Screen.Account(screen.username)) },
                        onClickLogin = { navigator.navigateTo(Screen.Admin) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                    )
                }
                entry<Screen.Account> { screen ->
                    AccountScreen(
                        username = screen.username,
                        platform = platform,
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
                        onClickOperator = { navigator.navigateTo(Screen.Account(it)) },
                    )
                }
                entry<Screen.NotFound> { screen ->
                    NotFoundScreen(
                        requestedPath = screen.path,
                        onClickHome = { navigator.navigateTo(Screen.Home) },
                        onClickAdmin = { navigator.navigateTo(Screen.Admin) },
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
