package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 管理画面ログイン用のパスワード入力。
 */
@Composable
fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    HiddenHtmlForm(
        formId = FORM_ID,
        onSubmit = onSubmit,
        enabled = enabled,
    )
    HtmlInputField(
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
