package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.format.UnixTimeUtil
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminInboxCoolOffsResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class AdminInboxScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi = AdminApi(),
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private var sessionJob: Job? = null
    private var coolOffsJob: Job? = null

    val uiStateFlow: StateFlow<AdminInboxScreenUiState> =
        MutableStateFlow(
            AdminInboxScreenUiState(
                content = AdminInboxScreenUiState.Content.Loading,
                listener = object : AdminInboxScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

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

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    private fun reload() {
        sessionJob?.cancel()
        coolOffsJob?.cancel()
        coolOffsJob = null
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (coolOffsJob == null) {
                        coolOffsJob = viewModelScope.launch {
                            api.inboxCoolOffs(historyLimit = HISTORY_LIMIT).collect { coolOffs ->
                                viewModelStateFlow.update { it.copy(coolOffs = coolOffs) }
                            }
                        }
                    }
                } else {
                    coolOffsJob?.cancel()
                    coolOffsJob = null
                    viewModelStateFlow.update { it.copy(coolOffs = null) }
                }
            }
        }
    }

    private fun createContent(state: ViewModelState): AdminInboxScreenUiState.Content {
        val session = state.session ?: return AdminInboxScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminInboxScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminInboxScreenUiState.Content.RequireLogin
            }
        }

        return when (val coolOffs = state.coolOffs) {
            null -> AdminInboxScreenUiState.Content.Loading

            is AdminInboxCoolOffsResult.Failure -> AdminInboxScreenUiState.Content.Error(coolOffs.message)

            is AdminInboxCoolOffsResult.Success -> {
                AdminInboxScreenUiState.Content.Loaded(
                    coolOffs = coolOffs.coolOffs.map { coolOff ->
                        AdminInboxScreenUiState.CoolOff(
                            clientIp = coolOff.clientIp,
                            untilText = "${UnixTimeUtil.format(coolOff.until)} まで通さない",
                            countText = "署名を拒否 ${coolOff.rejectedCount} 回 / " +
                                "通さずに返した ${coolOff.blockedRequestCount} 件",
                        )
                    },
                    coolOffEmptyText = "いま止めている送信元は無い。".takeIf { coolOffs.coolOffs.isEmpty() },
                    history = coolOffs.history.map { record ->
                        AdminInboxScreenUiState.Record(
                            clientIp = record.clientIp,
                            blockedAtText = UnixTimeUtil.format(record.blockedAt),
                            untilText = "${UnixTimeUtil.format(record.until)} まで",
                        )
                    },
                    historyEmptyText = "止めた記録は無い。".takeIf { coolOffs.history.isEmpty() },
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val coolOffs: AdminInboxCoolOffsResult? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        /**
         * 履歴として出す件数。サーバーが残している数より多くは返ってこない
         */
        const val HISTORY_LIMIT: Int = 50
    }
}
