@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package net.matsudamper.mastodon.rss.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal enum class HtmlInputType(val value: String) {
    Text("text"),
    Password("password"),
    Email("email"),
    Search("search"),
    Tel("tel"),
    Url("url"),
}

/**
 * プラットフォームの入力欄を Material3 の TextField 風に見せる。
 *
 * Web では HTML の input を埋め込み、オートフィルとクリップボード操作を
 * ブラウザに任せる。Android では [androidx.compose.material3.OutlinedTextField] を使う。
 */
@Composable
internal expect fun HtmlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inputId: String,
    inputName: String,
    inputType: HtmlInputType,
    autocomplete: String,
    enabled: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
    formId: String? = null,
    required: Boolean = false,
)

/**
 * 画面外の HTML form。オートフィルと Enter 送信のため。
 *
 * Web 以外では何もしない。[HtmlInputField] の `formId` と同じ id を渡す。
 */
@Composable
internal expect fun HiddenHtmlForm(
    formId: String,
    onSubmit: () -> Unit,
    enabled: Boolean,
)
