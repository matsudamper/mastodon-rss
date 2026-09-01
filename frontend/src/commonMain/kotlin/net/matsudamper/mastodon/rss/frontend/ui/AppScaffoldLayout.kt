package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 公開画面と管理画面で共通の枠。TopAppBar 以外のレイアウトだけを担う。
 */
@Composable
internal fun AppScaffoldLayout(
    snackbarHostState: SnackbarHostState,
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            topBar()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= WideBreakpoint

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content(wide)
                    }

                    ScaffoldSnackbarHost(
                        state = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .widthIn(max = SnackbarMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}
