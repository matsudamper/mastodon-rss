package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import net.matsudamper.mastodon.rss.frontend.logic.admin.AdminApi
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard

@Composable
internal fun AdminDeliveryTargetsScreen(
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminDeliveryTargetsScreenViewModel(viewModelScope, AdminApi())
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminDeliveryTargetsScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(uiState.listener) {
        uiState.listener.onStart()
    }

    AdminDeliveryTargetsContent(uiState = uiState)
}

@Composable
internal fun AdminDeliveryTargetsContent(
    uiState: AdminDeliveryTargetsScreenUiState,
) {
    AdminScaffold("ホストごとの配信先の上限", listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "ホストごとの配信先の上限",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = uiState.listener::onClickReload) { Text("更新") }
            }
            when (val content = uiState.content) {
                AdminDeliveryTargetsScreenUiState.Content.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                AdminDeliveryTargetsScreenUiState.Content.RequireLogin -> RequireLoginCard(
                    onClickAdmin = uiState.listener::onClickAdmin,
                )

                is AdminDeliveryTargetsScreenUiState.Content.Error -> SectionCard("上限を出せない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = uiState.listener::onClickReload) { Text("もう一度試す") }
                }

                is AdminDeliveryTargetsScreenUiState.Content.Loaded -> {
                    Limit(content = content)
                    Hosts(content = content)
                }
            }
        }
    }
}

@Composable
private fun Limit(content: AdminDeliveryTargetsScreenUiState.Content.Loaded) {
    SectionCard("上限") {
        Text(
            "同じサーバーからのフォローで配信する宛先の数の上限。超えるフォローは断る。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(content.limitText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Hosts(content: AdminDeliveryTargetsScreenUiState.Content.Loaded) {
    SectionCard("ホストごとの配信先") {
        Text(
            content.hostsDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content.hostsEmptyText?.let { Text(it) }
        content.hosts.forEachIndexed { index, host ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Host(host)
        }
    }
}

@Composable
private fun Host(host: AdminDeliveryTargetsScreenUiState.Host) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                host.host,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                host.countText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (host.countTextHighlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (host.countTextHighlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        host.limitReachedText?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
