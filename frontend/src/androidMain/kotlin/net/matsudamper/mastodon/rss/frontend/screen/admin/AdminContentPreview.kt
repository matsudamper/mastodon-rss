package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.mastodon.rss.frontend.screen.AndroidPreviewScreenPlatform
import net.matsudamper.mastodon.rss.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminContentPreview() {
    MaterialTheme {
        AdminContent(
            uiState = AdminScreenUiState(
                content = AdminScreenUiState.Content.LoggedIn(
                    sections = listOf(
                        AdminScreenUiState.MenuSection(
                            title = "アカウント",
                            items = listOf(
                                previewMenuItem("アカウントの一覧", "登録したアカウントを見る。"),
                                previewMenuItem("アカウントの追加", "フィードを流すアカウントを新しく作る。"),
                            ),
                        ),
                        AdminScreenUiState.MenuSection(
                            title = "フィード",
                            items = listOf(
                                AdminScreenUiState.MenuItem(
                                    title = "フィードの登録と再取得",
                                    description = "フィードの登録・削除と、手動での再取得。",
                                    availability = AdminScreenUiState.MenuItem.Availability.Planned(note = "これから作る。"),
                                ),
                            ),
                        ),
                    ),
                    listener = AndroidPreviewLoggedInListener,
                ),
                listener = AndroidPreviewAdminListener,
            ),
            platform = AndroidPreviewScreenPlatform,
        )
    }
}

private fun previewMenuItem(title: String, description: String): AdminScreenUiState.MenuItem {
    return AdminScreenUiState.MenuItem(
        title = title,
        description = description,
        availability = AdminScreenUiState.MenuItem.Availability.Available(
            listener = object : AdminScreenUiState.MenuItem.Listener {
                override fun onClick() = Unit
            },
        ),
    )
}

private object AndroidPreviewLoggedInListener : AdminScreenUiState.Content.LoggedIn.Listener {
    override fun onClickLogout() = Unit
}

private object AndroidPreviewAdminListener : AdminScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onClickRetry() = Unit
}
