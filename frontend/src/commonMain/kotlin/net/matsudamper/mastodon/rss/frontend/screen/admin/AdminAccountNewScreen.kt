package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

@Composable
internal fun AdminAccountNewScreen(
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminAccountNewScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminAccountNewScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminAccountNewContent(uiState = uiState)
}

@Composable
internal fun AdminAccountNewContent(
    uiState: AdminAccountNewScreenUiState,
) {
    AdminScaffold("アカウントの追加", listener = uiState.listener) { wide ->
        Column(
            Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("アカウントの追加", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            when (val content = uiState.content) {
                AdminAccountNewScreenUiState.Content.Loading -> SectionCard("確認中") { Text("状態を確かめている。") }

                AdminAccountNewScreenUiState.Content.RequireLogin -> RequireLoginCard(onClickAdmin = uiState.listener::onClickAdmin)

                is AdminAccountNewScreenUiState.Content.Error -> SectionCard("状態が分からない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                }

                is AdminAccountNewScreenUiState.Content.Input -> InputCard(content, uiState.listener)

                is AdminAccountNewScreenUiState.Content.Added -> AddedCard(
                    content = content,
                    listener = uiState.listener,
                )
            }
        }
    }
}

@Composable
private fun InputCard(content: AdminAccountNewScreenUiState.Content.Input, listener: AdminAccountNewScreenUiState.Listener) {
    SectionCard("ユーザー名") {
        Text("名前は後から変えられない。Mastodon は一度取得したアカウントを持ち続けるので、別の名前にしたい場合は作り直すことになる。")
        OutlinedTextField(
            value = content.username,
            onValueChange = listener::onUsernameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ユーザー名") },
            singleLine = true,
            enabled = !content.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { listener.onClickAdd() }),
            isError = content.error != null,
        )
        content.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = listener::onClickAdd, enabled = content.canSubmit) {
            Text(if (content.submitting) "追加中..." else "追加")
        }
    }
}

@Composable
private fun AddedCard(content: AdminAccountNewScreenUiState.Content.Added, listener: AdminAccountNewScreenUiState.Listener) {
    SectionCard("追加した") {
        Text("${content.acct} が Mastodon から検索できるようになった。")
        TextLink("一覧を見る", listener::onClickAccounts)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = listener::onClickAddAnother) { Text("続けて追加") }
        }
    }
}
