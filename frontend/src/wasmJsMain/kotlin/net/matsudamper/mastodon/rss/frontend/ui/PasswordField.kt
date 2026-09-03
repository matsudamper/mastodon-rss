package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HiddenHtmlForm
import net.matsudamper.frontend.component.HtmlInputType
import net.matsudamper.frontend.component.PlatformInputField

@Composable
internal actual fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    label: String,
    formId: String,
    inputId: String,
    inputName: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    HiddenHtmlForm(
        formId = formId,
        onSubmit = onSubmit,
        enabled = enabled,
    )
    PlatformInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        inputId = inputId,
        inputName = inputName,
        inputType = HtmlInputType.Password,
        autocomplete = "current-password",
        enabled = enabled,
        hasError = hasError,
        formId = formId,
        required = true,
        modifier = modifier,
    )
}
