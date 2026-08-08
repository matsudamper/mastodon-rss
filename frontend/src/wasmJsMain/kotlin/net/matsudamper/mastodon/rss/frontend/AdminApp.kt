package net.matsudamper.mastodon.rss.frontend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matsudamper.mastodon.rss.admin.api.AdminSessionResponse

/**
 * 管理画面の入口。
 *
 * 画面は URL で決まり、出せる画面はログイン状態で決まる。ログイン状態の取得は
 * 起動時に 1 回だけ行い、ログイン・ログアウトのたびに更新する。
 */
@Composable
internal fun AdminApp() {
    val api = remember { AdminApiClient() }

    var route by remember { mutableStateOf(AdminRouter.currentRoute()) }
    var session by remember { mutableStateOf<AdminSessionResponse?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // ブラウザの戻る・進むでも画面を合わせる
    DisposableEffect(Unit) {
        AdminRouter.onRouteChanged { route = it }
        onDispose { AdminRouter.clearRouteChangedListener() }
    }

    LaunchedEffect(Unit) {
        when (val result = api.session()) {
            is AdminResult.Success -> session = result.value
            is AdminResult.Failure -> loadError = result.message
        }
    }

    val navigate: (AdminRoute) -> Unit = { destination ->
        AdminRouter.navigate(destination)
        route = destination
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val current = session
            when {
                current == null -> {
                    StartupScreen(loadError)
                }

                route == AdminRoute.PasswordHash -> {
                    PasswordHashScreen(
                        api = api,
                        session = current,
                        onBack = { navigate(AdminRoute.Home) },
                    )
                }

                current.authenticated -> {
                    DashboardScreen(
                        api = api,
                        onSessionChanged = { session = it },
                        onOpenPasswordHash = { navigate(AdminRoute.PasswordHash) },
                    )
                }

                else -> {
                    LoginScreen(
                        api = api,
                        session = current,
                        onSessionChanged = { session = it },
                        onOpenPasswordHash = { navigate(AdminRoute.PasswordHash) },
                    )
                }
            }
        }
    }
}

/** ログイン状態を取りに行っている間の画面。取れなければ理由を出す */
@Composable
private fun StartupScreen(errorMessage: String?) {
    AdminPage(title = "mastodon-rss 管理画面") {
        if (errorMessage == null) {
            CircularProgressIndicator()
        } else {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 画面の共通の枠。
 *
 * 管理画面は入力欄が数個の画面しか無いので、横幅を決めて中央に置くだけにしている。
 */
@Composable
internal fun AdminPage(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}
