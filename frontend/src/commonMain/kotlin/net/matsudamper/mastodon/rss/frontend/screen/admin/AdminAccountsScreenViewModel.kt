package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.api.AdminAccountsResult
import net.matsudamper.mastodon.rss.frontend.api.AdminApi
import net.matsudamper.mastodon.rss.frontend.api.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.format.formatUnixTime

class AdminAccountsScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountsScreenUiState> =
        MutableStateFlow(
            AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loading,
                listener =
                object : AdminAccountsScreenUiState.Listener {
                    override fun onClickReload() {
                        reload()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        uiState.copy(content = createContent(viewModelState))
                    }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        viewModelStateFlow.update { ViewModelState() }
        viewModelScope.launch {
            // ログインしていなければ一覧は errors になる。先に確かめないと、
            // 「ログインが要る」ことをネットワークの失敗と区別できない
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session is AdminSessionResult.Success && session.loggedIn) {
                viewModelStateFlow.update { it.copy(accounts = api.accounts()) }
            }
        }
    }

    private fun createContent(state: ViewModelState): AdminAccountsScreenUiState.Content {
        val session = state.session ?: return AdminAccountsScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminAccountsScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountsScreenUiState.Content.RequireLogin
            }
        }

        return when (val accounts = state.accounts) {
            null -> AdminAccountsScreenUiState.Content.Loading

            is AdminAccountsResult.Failure -> AdminAccountsScreenUiState.Content.Error(accounts.message)

            is AdminAccountsResult.Success -> {
                AdminAccountsScreenUiState.Content.Loaded(
                    accounts = accounts.accounts.map { account ->
                        AdminAccountsScreenUiState.Account(
                            username = account.username,
                            acct = account.acct,
                            badge = if (account.deletable) null else NOT_DELETABLE_BADGE,
                            actorUrl = account.actorUrl,
                            createdAt = account.createdAt?.let { formatUnixTime(it) },
                        )
                    },
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val accounts: AdminAccountsResult? = null,
    )

    private companion object {
        /** 消せないアカウントに付ける印 */
        const val NOT_DELETABLE_BADGE = "消せない"
    }
}
