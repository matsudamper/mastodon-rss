package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
fun AdminAccountNewScreen(onNavigate: (Screen) -> Unit) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) { AdminAccountNewScreenViewModel(viewModelScope) }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LifecycleStartEffect(Unit) {
        viewModel.onStart()
        onStopOrDispose {}
    }

    AdminAccountNewScreen(uiState = uiState, onNavigate = onNavigate)
}

@Composable
private fun AdminAccountNewScreen(
    uiState: AdminAccountNewScreenUiState,
    onNavigate: (Screen) -> Unit,
) {
    AdminScaffold(title = "アカウントの追加", onNavigate = onNavigate) { _ ->
        Text(
            text = "アカウントの追加",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val content = uiState.content) {
            AdminAccountNewScreenUiState.Content.Loading -> {
                SectionCard(title = "確認中") {
                    Text(
                        text = "状態を確かめている。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            AdminAccountNewScreenUiState.Content.RequireLogin -> {
                RequireLoginCard(onNavigate = onNavigate)
            }

            is AdminAccountNewScreenUiState.Content.Error -> {
                SectionCard(title = "状態が分からない") {
                    Text(
                        text = content.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is AdminAccountNewScreenUiState.Content.Input -> {
                InputCard(content = content, listener = uiState.listener)
            }

            is AdminAccountNewScreenUiState.Content.Added -> {
                AddedCard(
                    content = content,
                    listener = uiState.listener,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun InputCard(
    content: AdminAccountNewScreenUiState.Content.Input,
    listener: AdminAccountNewScreenUiState.Listener,
) {
    SectionCard(title = "ユーザー名") {
        Text(
            text =
            "名前は後から変えられない。Mastodon は一度取得したアカウントを持ち続けるので、" +
                "別の名前にしたい場合は作り直すことになる。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = content.username,
            onValueChange = { listener.onUsernameChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ユーザー名") },
            singleLine = true,
            enabled = !content.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { listener.onClickAdd() }),
            isError = content.error != null,
        )

        if (content.error != null) {
            Text(
                text = content.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { listener.onClickAdd() },
            enabled = content.canSubmit,
        ) {
            Text(if (content.submitting) "追加中..." else "追加")
        }
    }
}

@Composable
private fun AddedCard(
    content: AdminAccountNewScreenUiState.Content.Added,
    listener: AdminAccountNewScreenUiState.Listener,
    onNavigate: (Screen) -> Unit,
) {
    SectionCard(title = "追加した") {
        Text(
            text = "${content.acct} が Mastodon から検索できるようになった。",
            style = MaterialTheme.typography.bodyMedium,
        )

        TextLink(
            text = "一覧を見る",
            onClick = { onNavigate(Screen.AdminAccounts) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { listener.onClickAddAnother() }) {
                Text("続けて追加")
            }
        }
    }
}
