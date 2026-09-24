package net.matsudamper.mastodon.rss.frontend.navigation

internal class WasmNavigator(
    private val navController: NavController,
    private val screenStateStore: ScreenStateStore,
) : Navigator {
    /**
     * アプリの中から開くのは新しく見に行くときなので、前に見たときの状態は引き継がない
     */
    override suspend fun navigate(screen: Screen) {
        screenStateStore.discard(screen)
        navController.navigateTo(screen)
    }

    override suspend fun back() {
        navController.back()
    }
}
