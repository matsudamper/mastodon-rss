package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccount
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult

class AdminAccountScreenViewModel(
    private val username: String,
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountScreenUiState> =
        MutableStateFlow(
            AdminAccountScreenUiState(
                acct = "@$username",
                content = AdminAccountScreenUiState.Content.Loading,
                listener = object : AdminAccountScreenUiState.Listener {
                    override fun onClickReload() {
                        reload()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState -> uiState.copy(content = createContent(viewModelState)) }
                }
            }
        }.asStateFlow()

    fun onStart() {
        reload()
    }

    private fun reload() {
        viewModelStateFlow.update { ViewModelState() }

        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }

            if (session !is AdminSessionResult.Success || !session.loggedIn) return@launch

            val account = api.account(username)
            viewModelStateFlow.update { it.copy(account = account) }
        }
    }

    private fun createContent(state: ViewModelState): AdminAccountScreenUiState.Content {
        val session = state.session ?: return AdminAccountScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> return AdminAccountScreenUiState.Content.Error(session.message)

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountScreenUiState.Content.RequireLogin
            }
        }

        return when (val account = state.account) {
            null -> AdminAccountScreenUiState.Content.Loading

            is AdminAccountResult.Failure -> AdminAccountScreenUiState.Content.Error(account.message)

            is AdminAccountResult.Success -> {
                val found = account.account ?: return AdminAccountScreenUiState.Content.NotFound

                AdminAccountScreenUiState.Content.Loaded(account = found.toUiState())
            }
        }
    }

    private fun AdminAccount.toUiState(): AdminAccountScreenUiState.Account = AdminAccountScreenUiState.Account(
        username = account.username,
        acct = account.acct,
        actorUrl = account.actorUrl,
        createdAt = createdAt?.let { UnixTimeUtil.format(it) },
        followerCount = followerCount,
    )

    // TODO: 投稿の送信と、配信した投稿の一覧の取得は Phase 4 でここに足す

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val account: AdminAccountResult? = null,
    )
}
