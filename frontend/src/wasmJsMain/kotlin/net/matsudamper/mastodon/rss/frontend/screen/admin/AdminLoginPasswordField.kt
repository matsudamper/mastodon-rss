package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.matsudamper.frontend.component.HiddenHtmlForm
import net.matsudamper.frontend.component.HtmlInputType
import net.matsudamper.frontend.component.PlatformInputField

@Composable
internal actual fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    HiddenHtmlForm(
        formId = FORM_ID,
        onSubmit = onSubmit,
        enabled = enabled,
    )
    PlatformInputField(
        value = password,
        onValueChange = onPasswordChange,
        label = "パスワード",
        inputId = PASSWORD_INPUT_ID,
        inputName = "password",
        inputType = HtmlInputType.Password,
        autocomplete = "current-password",
        enabled = enabled,
        hasError = hasError,
        formId = FORM_ID,
        required = true,
        modifier = modifier,
    )
}

private const val FORM_ID = "admin-login-form"
private const val PASSWORD_INPUT_ID = "admin-login-password"
