package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
)
