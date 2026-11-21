package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminDeliveryTargetsContentPreview() {
    MaterialTheme {
        AdminDeliveryTargetsContent(
            uiState = AdminDeliveryTargetsScreenUiState(
                content = AdminDeliveryTargetsScreenUiState.Content.Loaded(
                    limitText = "上限: 20",
                    hostsDescription = "配信する宛先の多い順に 100 件まで。",
                    hosts = listOf(
                        AdminDeliveryTargetsScreenUiState.Host(
                            host = "spam.example",
                            countText = "20 / 20",
                            countTextHighlighted = true,
                            limitReachedText = "上限に届いている。宛先が増えるフォローは断る。",
                        ),
                        AdminDeliveryTargetsScreenUiState.Host(
                            host = "mastodon.example",
                            countText = "1 / 20",
                            countTextHighlighted = false,
                            limitReachedText = null,
                        ),
                    ),
                    hostsEmptyText = null,
                ),
                listener = AndroidPreviewAdminDeliveryTargetsListener,
            ),
        )
    }
}

private object AndroidPreviewAdminDeliveryTargetsListener : AdminDeliveryTargetsScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickReload() = Unit
}
