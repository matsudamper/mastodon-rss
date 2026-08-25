@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
actual fun HtmlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputId: String,
    inputName: String,
    inputType: HtmlInputType,
    autocomplete: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier,
    formId: String?,
    required: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    val interactionSource = remember { MutableInteractionSource() }
    val focusInteractionHolder = remember { FocusInteractionHolder() }
    val clickInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = clickInteractionSource,
                indication = null,
                enabled = enabled,
            ) {
                (document.getElementById(inputId) as? HTMLInputElement)?.focus()
            },
    ) {
        MaterialTextFieldDefaults.DecorationBox(
            value = value,
            visualTransformation = VisualTransformation.None,
            innerTextField = {
                HtmlElementView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(InputLineHeight),
                    factory = {
                        val input = document.createElement("input") as HTMLInputElement
                        input.id = inputId
                        input.name = inputName
                        input.type = inputType.value
                        input.autocomplete = autocomplete
                        if (formId != null) {
                            input.setAttribute("form", formId)
                        }
                        input.setAttribute("aria-label", label)
                        input.required = required
                        input
                    },
                    update = { input ->
                        input.disabled = !enabled
                        applyInputStyle(
                            input = input,
                            fontSizeSp = textStyle.fontSize.value,
                            textColor = colors.onSurface,
                        )
                        bindFocusHandlers(
                            input = input,
                            interactionSource = interactionSource,
                            focusInteractionHolder = focusInteractionHolder,
                            onValueChange = onValueChange,
                        )
                        bindClipboardHandlers(input = input)
                        syncInputValue(input = input, value = value)
                        input.oninput = { event ->
                            onValueChange(readInputValue(event))
                        }
                        input.onchange = { event ->
                            onValueChange(readInputValue(event))
                        }
                    },
                )
            },
            label = {
                Text(label)
            },
            placeholder = null,
            leadingIcon = null,
            trailingIcon = null,
            prefix = null,
            suffix = null,
            supportingText = null,
            shape = MaterialTextFieldDefaults.shape,
            singleLine = true,
            enabled = enabled,
            isError = hasError,
            interactionSource = interactionSource,
            colors = MaterialTextFieldDefaults.colors(
                focusedSupportingTextColor = colors.surface,
                focusedContainerColor = colors.surface,
                focusedIndicatorColor = colors.primary,
                focusedTextColor = colors.onSurface,
                focusedLabelColor = colors.primary,
                focusedPrefixColor = colors.onSurfaceVariant,
                focusedSuffixColor = colors.onSurfaceVariant,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                focusedTrailingIconColor = colors.onSurfaceVariant,
                focusedLeadingIconColor = colors.onSurfaceVariant,
            ),
        )
    }
}

@Composable
actual fun HiddenHtmlForm(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
) {
    DisposableEffect(formId, enabled) {
        val form = document.getElementById(formId) as HTMLFormElement?
            ?: run {
                val created = document.createElement("form") as HTMLFormElement
                created.id = formId
                created.setAttribute("autocomplete", "on")
                created.style.display = "none"
                document.body?.appendChild(created)
                created
            }
        form.onsubmit = { event ->
            event.preventDefault()
            if (enabled) {
                onSubmit()
            }
        }
        onDispose {
            form.onsubmit = null
        }
    }
}

private class FocusInteractionHolder {
    var focus: FocusInteraction.Focus? = null
}

private fun bindClipboardHandlers(input: HTMLInputElement) {
    input.onkeydown = { event ->
        if (event.isClipboardShortcutKey()) {
            event.stopPropagation()
        }
    }
}

private fun syncInputValue(
    input: HTMLInputElement,
    value: String,
) {
    if (document.activeElement == input) return
    if (input.value != value) {
        input.value = value
    }
}

private fun KeyboardEvent.isClipboardShortcutKey(): Boolean {
    if (type != "keydown") return false
    if (!ctrlKey && !metaKey) return false
    return when (key.lowercase()) {
        "x", "c", "v", "a" -> true
        else -> false
    }
}

private fun bindFocusHandlers(
    input: HTMLInputElement,
    interactionSource: MutableInteractionSource,
    focusInteractionHolder: FocusInteractionHolder,
    onValueChange: (String) -> Unit,
) {
    input.onfocus = {
        if (focusInteractionHolder.focus == null) {
            val focus = FocusInteraction.Focus()
            focusInteractionHolder.focus = focus
            interactionSource.tryEmit(focus)
        }
    }
    input.onblur = {
        val focus = focusInteractionHolder.focus
        if (focus != null) {
            interactionSource.tryEmit(FocusInteraction.Unfocus(focus))
            focusInteractionHolder.focus = null
        }
        onValueChange(input.value)
    }
}

private fun applyInputStyle(
    input: HTMLInputElement,
    fontSizeSp: Float,
    textColor: Color,
) {
    input.style.apply {
        boxSizing = "border-box"
        display = "block"
        width = "100%"
        height = "100%"
        margin = "0"
        padding = "0"
        border = "none"
        outline = "none"
        backgroundColor = "transparent"
        color = textColor.toCssColor()
        setProperty("appearance", "none")
        setProperty("-webkit-appearance", "none")
        fontSize = "${fontSizeSp}px"
        lineHeight = "normal"
        fontFamily = "inherit"
        letterSpacing = "normal"
    }
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

private val InputLineHeight = 24.dp
