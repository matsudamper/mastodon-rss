package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.events.Event

/**
 * 管理画面ログイン用のパスワード入力。
 *
 * canvas 上の [androidx.compose.material3.OutlinedTextField] ではブラウザの
 * パスワードマネージャーがフィールドを認識できないため、HTML の form/input を埋め込む。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AdminLoginPasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.bodySmall
    val inputStyle = MaterialTheme.typography.bodyLarge

    HtmlElementView(
        modifier = modifier,
        factory = {
            val form = document.createElement("form") as HTMLFormElement
            form.id = FORM_ID
            form.setAttribute("autocomplete", "on")

            val label = document.createElement("label") as HTMLLabelElement
            label.htmlFor = PASSWORD_INPUT_ID
            label.textContent = "パスワード"

            val passwordInput = document.createElement("input") as HTMLInputElement
            passwordInput.id = PASSWORD_INPUT_ID
            passwordInput.name = "password"
            passwordInput.type = "password"
            passwordInput.autocomplete = "current-password"
            passwordInput.required = true

            form.append(label, passwordInput)
            form
        },
        update = { form ->
            val label = form.querySelector("label") as HTMLLabelElement
            val passwordInput = form.querySelector("#$PASSWORD_INPUT_ID") as HTMLInputElement

            label.style.apply {
                display = "block"
                marginBottom = "4px"
                color = colors.onSurfaceVariant.toCssColor()
                fontSize = "${labelStyle.fontSize.value}px"
                fontFamily = labelStyle.fontFamily?.toString() ?: "inherit"
            }

            passwordInput.disabled = !enabled
            passwordInput.style.apply {
                boxSizing = "border-box"
                width = "100%"
                minHeight = "56px"
                padding = "16px 12px"
                borderRadius = "4px"
                border = "1px solid ${if (hasError) colors.error.toCssColor() else colors.outline.toCssColor()}"
                backgroundColor = colors.surface.toCssColor()
                color = colors.onSurface.toCssColor()
                fontSize = "${inputStyle.fontSize.value}px"
                fontFamily = inputStyle.fontFamily?.toString() ?: "inherit"
            }

            if (passwordInput.value != password) {
                passwordInput.value = password
            }

            passwordInput.oninput = { event ->
                val value = readInputValue(event)
                if (value != password) {
                    onPasswordChange(value)
                }
            }
            passwordInput.onchange = { event ->
                val value = readInputValue(event)
                if (value != password) {
                    onPasswordChange(value)
                }
            }

            form.onsubmit = { event ->
                event.preventDefault()
                if (enabled) {
                    onSubmit()
                }
            }
        },
    )
}

private fun readInputValue(event: Event): String {
    val target = event.target
    return (target as? HTMLInputElement)?.value ?: ""
}

private fun Color.toCssColor(): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgb($r, $g, $b)"
}

private const val FORM_ID = "admin-login-form"
private const val PASSWORD_INPUT_ID = "admin-login-password"
