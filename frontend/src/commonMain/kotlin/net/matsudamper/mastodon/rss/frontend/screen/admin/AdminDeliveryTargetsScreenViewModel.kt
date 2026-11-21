package net.matsudamper.mastodon.rss.frontend.screen.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.mastodon.rss.frontend.event.EventSender
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminDeliveryTargetLimitResult
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSessionResult
import net.matsudamper.mastodon.rss.frontend.navigation.Screen

class AdminDeliveryTargetsScreenViewModel(
    private val viewModelScope: CoroutineScope,
    private val api: AdminApi,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> = MutableStateFlow(ViewModelState())
    private var sessionJob: Job? = null
    private var limitJob: Job? = null

    val uiStateFlow: StateFlow<AdminDeliveryTargetsScreenUiState> =
        MutableStateFlow(
            AdminDeliveryTargetsScreenUiState(
                content = AdminDeliveryTargetsScreenUiState.Content.Loading,
                listener = object : AdminDeliveryTargetsScreenUiState.Listener {
                    override fun onStart() {
                        reload()
                    }

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

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    private fun reload() {
        sessionJob?.cancel()
        limitJob?.cancel()
        viewModelStateFlow.update { ViewModelState() }
        sessionJob = viewModelScope.launch {
            api.session().collect { session ->
                viewModelStateFlow.update { it.copy(session = session) }

                if (session is AdminSessionResult.Success && session.loggedIn) {
                    if (viewModelStateFlow.value.limit == null) fetchLimit()
                } else {
                    limitJob?.cancel()
                    viewModelStateFlow.update { it.copy(limit = null) }
                }
            }
        }
    }

    private fun fetchLimit() {
        limitJob?.cancel()
        limitJob = viewModelScope.launch {
            val limit = api.deliveryTargetLimit(hostsLimit = HOSTS_LIMIT)
            viewModelStateFlow.update { it.copy(limit = limit) }
        }
    }

    private fun createContent(state: ViewModelState): AdminDeliveryTargetsScreenUiState.Content {
        val session = state.session ?: return AdminDeliveryTargetsScreenUiState.Content.Loading

        when (session) {
            is AdminSessionResult.Failure -> {
                return AdminDeliveryTargetsScreenUiState.Content.Error(session.message)
            }

            is AdminSessionResult.Success -> {
                if (!session.loggedIn) return AdminDeliveryTargetsScreenUiState.Content.RequireLogin
            }
        }

        return when (val limit = state.limit) {
            null -> AdminDeliveryTargetsScreenUiState.Content.Loading

            is AdminDeliveryTargetLimitResult.Failure -> AdminDeliveryTargetsScreenUiState.Content.Error(limit.message)

            is AdminDeliveryTargetLimitResult.Success -> {
                val max = limit.maxDeliveryTargetsPerHost
                AdminDeliveryTargetsScreenUiState.Content.Loaded(
                    limitText = "上限: $max",
                    hostsDescription = "配信する宛先の多い順に $HOSTS_LIMIT 件まで。同じサーバーの利用者がまとめて受け取る宛先を持っていれば、何人いても 1 つに数える。",
                    hosts = limit.hosts.map { host ->
                        val limitReached = host.deliveryTargetCount >= max
                        AdminDeliveryTargetsScreenUiState.Host(
                            host = host.host,
                            countText = "${host.deliveryTargetCount} / $max",
                            countTextHighlighted = limitReached,
                            limitReachedText = when {
                                host.deliveryTargetCount > max -> "上限を超えている。宛先が増えるフォローは断る。"
                                limitReached -> "上限に届いている。宛先が増えるフォローは断る。"
                                else -> null
                            },
                        )
                    },
                    hostsEmptyText = "フォローしているサーバーは無い。".takeIf { limit.hosts.isEmpty() },
                )
            }
        }
    }

    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val limit: AdminDeliveryTargetLimitResult? = null,
    )

    interface Event {
        suspend fun navigate(screen: Screen)
    }

    private companion object {
        /**
         * 一覧に出すホストの数。上限に近いホストが見えれば足りるので続きは取らない
         */
        const val HOSTS_LIMIT = 100
    }
}
