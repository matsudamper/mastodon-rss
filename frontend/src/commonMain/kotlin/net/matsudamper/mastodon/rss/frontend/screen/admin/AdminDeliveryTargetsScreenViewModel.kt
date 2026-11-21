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
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminSaveMaxDeliveryTargetsPerHostResult
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
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }

                    override fun onClickReload() {
                        reload()
                    }

                    override fun onLimitInputChanged(text: String) {
                        viewModelStateFlow.update { state ->
                            if (state.saving) return@update state
                            state.copy(limitInput = text, saveMessage = null, saveErrorMessage = null)
                        }
                    }

                    override fun onClickSave() {
                        save()
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

    /**
     * 入力欄は、取り直した上限で置き換える。保存した直後に、保存した値と入力が食い違って見えないようにする
     */
    private fun fetchLimit() {
        limitJob?.cancel()
        limitJob = viewModelScope.launch {
            val limit = api.deliveryTargetLimit(hostsLimit = HOSTS_LIMIT)
            viewModelStateFlow.update { state ->
                state.copy(
                    limit = limit,
                    limitInput = if (limit is AdminDeliveryTargetLimitResult.Success) {
                        limit.maxDeliveryTargetsPerHost.toString()
                    } else {
                        state.limitInput
                    },
                )
            }
        }
    }

    private fun save() {
        val state = viewModelStateFlow.value
        if (state.saving) return
        val value = state.limitInput.trim().toIntOrNull() ?: return
        viewModelStateFlow.update { it.copy(saving = true, saveMessage = null, saveErrorMessage = null) }

        viewModelScope.launch {
            when (val result = api.saveMaxDeliveryTargetsPerHost(value)) {
                is AdminSaveMaxDeliveryTargetsPerHostResult.Success -> {
                    viewModelStateFlow.update {
                        it.copy(saving = false, saveMessage = "上限を ${result.maxDeliveryTargetsPerHost} にした。次に受けるフォローから効く。")
                    }
                    fetchLimit()
                }

                is AdminSaveMaxDeliveryTargetsPerHostResult.OutOfRange -> {
                    viewModelStateFlow.update {
                        it.copy(saving = false, saveErrorMessage = "${result.minValue} から ${result.maxValue} の間で指定する")
                    }
                }

                is AdminSaveMaxDeliveryTargetsPerHostResult.Failure -> {
                    viewModelStateFlow.update { it.copy(saving = false, saveErrorMessage = result.message) }
                }
            }
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
                val inputValue = state.limitInput.trim().toIntOrNull()
                AdminDeliveryTargetsScreenUiState.Content.Loaded(
                    currentLimitText = "いまの上限: $max",
                    limitInput = state.limitInput,
                    limitInputEnabled = !state.saving,
                    saveButtonText = if (state.saving) "保存中" else "保存",
                    saveButtonEnabled = !state.saving && inputValue != null && inputValue != max,
                    saveMessage = state.saveMessage,
                    saveErrorMessage = state.saveErrorMessage,
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

    /**
     * @param limitInput 上限の入力欄。取ってきた上限で埋め、その後は手で書き換えたものを持つ
     */
    private data class ViewModelState(
        val session: AdminSessionResult? = null,
        val limit: AdminDeliveryTargetLimitResult? = null,
        val limitInput: String = "",
        val saving: Boolean = false,
        val saveMessage: String? = null,
        val saveErrorMessage: String? = null,
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
