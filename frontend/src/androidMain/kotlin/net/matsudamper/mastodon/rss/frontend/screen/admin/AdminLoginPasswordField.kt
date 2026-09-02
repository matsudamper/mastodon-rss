package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
internal actual fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        enabled = enabled,
        isError = hasError,
        label = { Text("パスワード") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = modifier,
    )
}
