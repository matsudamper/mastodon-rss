package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.LoadMoreOnScrollEnd
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
internal fun AdminDeliveriesScreen(
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminDeliveriesScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminDeliveriesScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminDeliveriesContent(uiState = uiState)
}

@Composable
internal fun AdminDeliveriesContent(
    uiState: AdminDeliveriesScreenUiState,
) {
    AdminScaffold("送り直しを待っている配信", listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "送り直しを待っている配信",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = uiState.listener::onClickReload) { Text("更新") }
            }
            when (val content = uiState.content) {
                AdminDeliveriesScreenUiState.Content.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                AdminDeliveriesScreenUiState.Content.RequireLogin -> RequireLoginCard(
                    onClickAdmin = uiState.listener::onClickAdmin,
                )

                is AdminDeliveriesScreenUiState.Content.Error -> SectionCard("一覧を出せない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = uiState.listener::onClickReload) { Text("もう一度試す") }
                }

                is AdminDeliveriesScreenUiState.Content.Loaded -> Deliveries(
                    content = content,
                    listener = uiState.listener,
                )
            }
        }
    }
}

@Composable
private fun Deliveries(
    content: AdminDeliveriesScreenUiState.Content.Loaded,
    listener: AdminDeliveriesScreenUiState.Listener,
) {
    content.emptyText?.let {
        SectionCard("送り直しを待っている配信") { Text(it) }
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("送り直しを待っている配信") {
            content.deliveries.forEachIndexed { index, delivery ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Delivery(delivery)
            }
        }

        if (content.loadMoreVisible) {
            LoadMoreOnScrollEnd(
                scrollState = scrollState,
                loadMoreOnVisible = content.loadMoreOnVisible,
                itemCount = content.deliveries.size,
                onLoadMore = listener::onLoadMore,
            )
            val errorMessage = content.loadMoreErrorMessage
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = listener::onLoadMore) {
                        Text("もう一度試す")
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Delivery(delivery: AdminDeliveriesScreenUiState.Delivery) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(delivery.kindText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                delivery.acct,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(delivery.inbox, style = MaterialTheme.typography.bodySmall)
        Text(
            delivery.statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            delivery.attemptsText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        delivery.lastError?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
