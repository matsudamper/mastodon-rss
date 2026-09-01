package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val SnackbarMaxWidth = 360.dp

private const val SnackbarVisibleDurationMillis = 4_000L

@Stable
class SnackbarHostState(
    private val scope: CoroutineScope,
) {
    private var dismissJob: Job? = null
    private val messageState = mutableStateOf<String?>(null)
    val message: State<String?> = messageState

    fun show(text: String) {
        dismissJob?.cancel()
        messageState.value = text
        dismissJob =
            scope.launch {
                delay(SnackbarVisibleDurationMillis)
                if (messageState.value == text) {
                    dismiss()
                }
            }
    }

    fun dismiss() {
        dismissJob?.cancel()
        dismissJob = null
        messageState.value = null
    }
}

@Composable
internal fun rememberSnackbarHostState(): SnackbarHostState {
    val scope = rememberCoroutineScope()
    return remember(scope) { SnackbarHostState(scope) }
}

@Composable
internal fun ScaffoldSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val message = state.message.value ?: return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = state::dismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "閉じる",
                )
            }
        }
    }
}
