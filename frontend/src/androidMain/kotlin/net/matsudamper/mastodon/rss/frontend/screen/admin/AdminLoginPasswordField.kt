package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier,
    )
}
