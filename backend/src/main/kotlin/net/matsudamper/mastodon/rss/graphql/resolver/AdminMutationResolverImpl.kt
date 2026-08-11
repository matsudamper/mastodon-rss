package net.matsudamper.mastodon.rss.graphql.resolver

import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.admin.appendSessionCookie
import net.matsudamper.mastodon.rss.admin.expireSessionCookie
import net.matsudamper.mastodon.rss.admin.sessionToken
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.applicationCall
import net.matsudamper.mastodon.rss.graphql.model.AdminMutationResolver
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginFailure
import net.matsudamper.mastodon.rss.graphql.model.QlAdminLoginResult
import net.matsudamper.mastodon.rss.graphql.model.QlAdminMutation
import net.matsudamper.mastodon.rss.graphql.model.QlAdminSession

/**
 * `login` はログインしていなくても叩ける。ログインする手段が他に無いので。
 *
 * パスワードの照合で PBKDF2 を回すぶん応答まで一拍あるが、口
 * （`graphQlRoutes`）が `Dispatchers.IO` に載せているので他のリクエストは詰まらない。
 */
class AdminMutationResolverImpl(
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
    private val cookieSecure: Boolean,
) : AdminMutationResolver {
    override fun login(
        adminMutation: QlAdminMutation,
        password: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminLoginResult>> {
        val call = env.applicationCall()

        if (passwordHash == null) {
            return completed(call.loginFailure(QlAdminLoginFailure.NOT_CONFIGURED))
        }

        if (!passwordHash.matches(password)) {
            return completed(call.loginFailure(QlAdminLoginFailure.WRONG_PASSWORD))
        }

        call.appendSessionCookie(
            token = sessions.create(),
            maxAgeSeconds = sessions.ttlSeconds,
            secure = cookieSecure,
        )

        // 発行した Cookie はまだリクエスト側に無いので、読み直して組み立てることはできない
        return completed(
            QlAdminLoginResult(
                session = QlAdminSession(loggedIn = true, passwordConfigured = true),
                failure = null,
            ),
        )
    }

    override fun logout(
        adminMutation: QlAdminMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val call = env.applicationCall()

        // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
        sessions.remove(call.sessionToken())
        call.expireSessionCookie(secure = cookieSecure)

        return completed(
            QlAdminSession(loggedIn = false, passwordConfigured = passwordHash != null),
        )
    }

    private fun ApplicationCall.loginFailure(failure: QlAdminLoginFailure): QlAdminLoginResult {
        return QlAdminLoginResult(
            session = adminSession(this, passwordHash, sessions),
            failure = failure,
        )
    }
}
