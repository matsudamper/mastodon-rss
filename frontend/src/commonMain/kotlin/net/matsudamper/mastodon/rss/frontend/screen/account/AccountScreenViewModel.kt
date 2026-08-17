package net.matsudamper.mastodon.rss.frontend.screen.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountApi
import net.matsudamper.mastodon.rss.frontend.logic.account.AccountResult

/**
 * @param username URL に入っていた名前。綴りが違っていても引けるので、
 *   画面に出すのは取ってきた方の名前にする
 * @param host 画面を開いているホスト。仮の値の組み立てにだけ使う
 */
class AccountScreenViewModel(
    private val username: String,
    private val host: String,
    private val viewModelScope: CoroutineScope,
    private val api: AccountApi = AccountApi(),
) {
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    private var loadingJob: Job? = null

    val uiStateFlow: StateFlow<AccountScreenUiState> =
        MutableStateFlow(
            AccountScreenUiState(
                content = AccountScreenUiState.Content.Loading,
                listener =
                object : AccountScreenUiState.Listener {
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
        loadingJob?.cancel()
        viewModelStateFlow.update { ViewModelState() }
        loadingJob =
            viewModelScope.launch {
                val account = api.account(username)
                viewModelStateFlow.update { it.copy(account = account) }
            }
    }

    private fun createContent(state: ViewModelState): AccountScreenUiState.Content {
        return when (val account = state.account) {
            null -> AccountScreenUiState.Content.Loading

            AccountResult.NotFound -> AccountScreenUiState.Content.NotFound

            is AccountResult.Failure -> AccountScreenUiState.Content.Error(account.message)

            is AccountResult.Success -> {
                AccountScreenUiState.Content.Loaded(
                    AccountUiState.placeholder(
                        username = account.account.username,
                        acct = account.account.acct,
                        actorUrl = account.account.actorUrl,
                        host = host,
                    ),
                )
            }
        }
    }

    private data class ViewModelState(
        val account: AccountResult? = null,
    )
}
