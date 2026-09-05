package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Immutable

data class AdminAccountProfileEditScreenUiState(
    val displayName: String,
    val summary: String,
    val inputEnabled: Boolean,
    val saving: Boolean,
    val saveButtonEnabled: Boolean,
    val closeEnabled: Boolean,
    val errorMessage: String?,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onDisplayNameChanged(text: String)
        fun onSummaryChanged(text: String)
        fun onClickSave()
        fun onClickClose()
    }
}
