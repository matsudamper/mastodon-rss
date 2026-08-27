package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 公開画面と管理画面で共通の枠。TopAppBar 以外のレイアウトだけを担う。
 */
@Composable
internal fun AppScaffoldLayout(
    topBar: @Composable () -> Unit,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    val snackbarHostState = rememberSnackbarHostState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            topBar()

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= WideBreakpoint
                val outerPadding = if (wide) 24.dp else 12.dp

                CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = outerPadding, vertical = outerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier =
                            Modifier
                                .widthIn(max = ContentMaxWidth)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = { content(wide) },
                        )
                    }

                    ScaffoldSnackbarHost(
                        state = snackbarHostState,
                        modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(horizontal = outerPadding, vertical = outerPadding)
                            .widthIn(max = SnackbarMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}
