package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminAddAccountResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.NavigationHandler
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.navigation.ScreenNavigator

class AdminAccountNewScreenViewModel(
    private val viewModelScope: CoroutineScope,
    navigationEvents: EventSender<NavigationHandler>,
    private val api: AdminApi = AdminApi(),
) {
    private val navigator = ScreenNavigator(navigationEvents, viewModelScope)
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())

    val uiStateFlow: StateFlow<AdminAccountNewScreenUiState> =
        MutableStateFlow(
            AdminAccountNewScreenUiState(
                content = AdminAccountNewScreenUiState.Content.Loading,
                listener =
                object : AdminAccountNewScreenUiState.Listener {
                    override fun onClickHome() {
                        navigator.navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigator.navigate(Screen.Admin)
                    }

                    override fun onClickAccounts() {
                        navigator.navigate(Screen.AdminAccounts)
                    }

                    override fun onUsernameChanged(text: String) {
                        viewModelStateFlow.update { it.copy(username = text, error = null) }
                    }

                    override fun onClickAdd() {
                        add()
                    }

                    override fun onClickAddAnother() {
                        viewModelStateFlow.update { it.copy(added = null, username = "", error = null) }
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
        viewModelScope.launch {
            val session = api.session()
            viewModelStateFlow.update { it.copy(session = session) }
        }
    }

    private fun add() {
        val state = viewModelStateFlow.value
        if (state.submitting || state.username.isBlank()) return

        viewModelStateFlow.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = api.addAccount(state.username.trim())) {
                is AdminAddAccountResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(submitting = false, added = result.acct)
                    }
                }

                is AdminAddAccountResult.Rejected -> {
                    failed(rejectedMessage(result))
                }

                is AdminAddAccountResult.Failure -> {
                    failed(result.message)
                }
            }
        }
    }

    private fun failed(message: String) {
        viewModelStateFlow.update { it.copy(submitting = false, error = message) }
    }

    /**
     * 当てはまる理由を全部並べる。1 つ直しても次で弾かれるのが分からないと直しようがない
     */
    private fun rejectedMessage(rejected: AdminAddAccountResult.Rejected): String = buildList {
        if (rejected.unusableCharacters.isNotEmpty()) {
            add("使えない文字が入っている: ${rejected.unusableCharacters.joinToString(" ")}")
        }
        if (rejected.minLength != null) add("${rejected.minLength} 文字以上にする")
        if (rejected.maxLength != null) add("${rejected.maxLength} 文字までにする")
        if (rejected.isDuplicated) add("同じ名前のアカウントが既にある")
    }.joinToString("\n").ifEmpty { "追加できなかった" }

    private fun createContent(state: ViewModelState): AdminAccountNewScreenUiState.Content {
        val session = state.session ?: return AdminAccountNewScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminAccountNewScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminAccountNewScreenUiState.Content.RequireLogin
            }
        }

        if (state.added != null) return AdminAccountNewScreenUiState.Content.Added(state.added)

        return AdminAccountNewScreenUiState.Content.Input(
            username = state.username,
            submitting = state.submitting,
            error = state.error,
        )
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val username: String = "",
        val submitting: Boolean = false,
        val error: String? = null,
        val added: String? = null,
    )
}
