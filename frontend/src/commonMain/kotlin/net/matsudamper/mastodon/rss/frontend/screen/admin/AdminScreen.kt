package net.matsudamper.mastodon.rss.frontend.screen.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.frontend.navigation.Navigator
import net.matsudamper.mastodon.rss.frontend.navigation.Screen
import net.matsudamper.mastodon.rss.frontend.screen.ScreenPlatform
import net.matsudamper.mastodon.rss.frontend.ui.AdminScaffold
import net.matsudamper.mastodon.rss.frontend.ui.AppBadge
import net.matsudamper.mastodon.rss.frontend.ui.ContentMaxWidth
import net.matsudamper.mastodon.rss.frontend.ui.PasswordField
import net.matsudamper.mastodon.rss.frontend.ui.SectionCard
import net.matsudamper.mastodon.rss.frontend.ui.TextLink

private const val REPOSITORY_URL = "https://github.com/matsudamper/mastodon-rss"
private const val LOGIN_FORM_ID = "admin-login-form"
private const val LOGIN_PASSWORD_INPUT_ID = "admin-login-password"

@Composable
internal fun AdminScreen(
    platform: ScreenPlatform,
    navController: Navigator,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope) {
        AdminScreenViewModel(viewModelScope)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navController) {
        viewModel.eventHandler.collect(
            object : AdminScreenViewModel.Event {
                override suspend fun navigate(screen: Screen) {
                    navController.navigate(screen)
                }
            },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onStart()
    }

    AdminContent(
        uiState = uiState,
        platform = platform,
    )
}

@Composable
internal fun AdminContent(
    uiState: AdminScreenUiState,
    platform: ScreenPlatform,
) {
    AdminScaffold(title = null, listener = uiState.listener) { wide ->
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (wide) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val content = uiState.content
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("管理画面", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (content is AdminScreenUiState.Content.LoggedIn) {
                    OutlinedButton(onClick = content.listener::onClickLogout) { Text("ログアウト") }
                }
            }
            when (content) {
                AdminScreenUiState.Content.Loading -> SectionCard(title = "確認中") {
                    Text("状態を確かめている。")
                }

                is AdminScreenUiState.Content.Login -> LoginCard(content, uiState.listener)

                is AdminScreenUiState.Content.LoggedIn -> {
                    content.sections.forEach { section ->
                        MenuSection(section = section, wide = wide)
                    }
                    SectionCard(title = "このソフトウェア") {
                        Text("ソースコードは GitHub で公開している。")
                        TextLink(
                            text = "mastodon-rss",
                            onClick = { platform.openExternalLink(REPOSITORY_URL) },
                        )
                    }
                }

                is AdminScreenUiState.Content.Error -> SectionCard(title = "状態が分からない") {
                    Text(content.message, color = MaterialTheme.colorScheme.error)
                    Text(
                        "サーバーが動いているかを確かめてから、もう一度試す。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = uiState.listener::onClickRetry) { Text("もう一度確かめる") }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    content: AdminScreenUiState.Content.Login,
    listener: AdminScreenUiState.Listener,
) {
    SectionCard(title = "ログイン") {
        Text(
            when (val input = content.input) {
                AdminScreenUiState.Content.Login.Input.Enabled -> "管理画面のパスワードを入れる。"
                is AdminScreenUiState.Content.Login.Input.Disabled -> input.message
            },
        )
        PasswordField(
            value = content.password,
            onValueChange = listener::onPasswordChanged,
            onSubmit = listener::onClickLogin,
            label = "パスワード",
            formId = LOGIN_FORM_ID,
            inputId = LOGIN_PASSWORD_INPUT_ID,
            inputName = "password",
            enabled = content.passwordInputEnabled,
            hasError = content.error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        content.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = listener::onClickLogin,
            enabled = content.loginButtonEnabled,
        ) {
            Text(if (content.submitting) "確認中..." else "ログイン")
        }
    }
}

@Composable
private fun MenuSection(
    section: AdminScreenUiState.MenuSection,
    wide: Boolean,
) {
    val columns = if (wide) 2 else 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        section.items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    MenuTile(item = item, modifier = Modifier.weight(1f))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MenuTile(
    item: AdminScreenUiState.MenuItem,
    modifier: Modifier = Modifier,
) {
    val availability = item.availability
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when (availability) {
                    is AdminScreenUiState.MenuItem.Availability.Available -> Modifier.clickable(onClick = availability.listener::onClick)
                    is AdminScreenUiState.MenuItem.Availability.Planned -> Modifier
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = when (availability) {
            is AdminScreenUiState.MenuItem.Availability.Available -> MaterialTheme.colorScheme.surface
            is AdminScreenUiState.MenuItem.Availability.Planned -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (availability) {
                        is AdminScreenUiState.MenuItem.Availability.Available -> MaterialTheme.colorScheme.primary
                        is AdminScreenUiState.MenuItem.Availability.Planned -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (availability is AdminScreenUiState.MenuItem.Availability.Planned) {
                    AppBadge(
                        text = "準備中",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (availability is AdminScreenUiState.MenuItem.Availability.Planned) {
                Text(
                    text = availability.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
