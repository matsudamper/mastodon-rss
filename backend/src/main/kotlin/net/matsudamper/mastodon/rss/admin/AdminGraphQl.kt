package net.matsudamper.mastodon.rss.admin

import graphql.schema.idl.RuntimeWiring
import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.applicationCall
import net.matsudamper.mastodon.rss.graphql.GraphQlWiring

/**
 * 管理画面のフィールドの結線。`Query.admin` と `Mutation.admin` の下にまとめる。
 *
 * `session` と `login` はログインしていなくても叩ける。ログインした人だけが
 * 叩けるものを足すときは [requireLoggedIn] を通す。
 *
 * 総当たり対策（試行回数の制限）はまだ無い。Phase 7 で入れる。
 *
 * @param passwordHash 未設定なら null。この場合はログインできない
 */
class AdminGraphQl(
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
    private val cookieSecure: Boolean,
) : GraphQlWiring {
    override fun contribute(builder: RuntimeWiring.Builder) {
        // null を返すと non-null 違反で下まで到達しない
        builder.type("Query") { it.dataFetcher("admin", { NAMESPACE }) }
        builder.type("Mutation") { it.dataFetcher("admin", { NAMESPACE }) }

        builder.type("AdminQuery") {
            it.dataFetcher("session", { env -> env.applicationCall().sessionState() })
        }

        builder.type("AdminMutation") {
            it
                .dataFetcher("login", { env ->
                    // スキーマで String! なので、検証を通った時点で必ず入っている
                    val password = requireNotNull(env.getArgument<String>("password")) { "password が無い" }
                    login(env.applicationCall(), password)
                })
                .dataFetcher("logout", { env -> logout(env.applicationCall()) })
        }
    }

    /** 失敗の理由を分けるのは、何を直せばよいのかが画面から分かるようにするため */
    private fun login(
        call: ApplicationCall,
        password: String,
    ): Map<String, Any?> {
        if (passwordHash == null) {
            return mapOf("session" to call.sessionState(), "failure" to FAILURE_NOT_CONFIGURED)
        }

        if (!passwordHash.matches(password)) {
            return mapOf("session" to call.sessionState(), "failure" to FAILURE_WRONG_PASSWORD)
        }

        call.appendSessionCookie(
            token = sessions.create(),
            maxAgeSeconds = sessions.ttlSeconds,
            secure = cookieSecure,
        )

        // 発行した Cookie はまだリクエスト側に無いので sessionState() は使えない
        return mapOf(
            "session" to mapOf("loggedIn" to true, "passwordConfigured" to true),
            "failure" to null,
        )
    }

    private fun logout(call: ApplicationCall): Map<String, Any?> {
        // Cookie を消すだけだと、値を控えられていた場合に使い続けられる
        sessions.remove(call.sessionToken())
        call.expireSessionCookie(secure = cookieSecure)

        return mapOf("loggedIn" to false, "passwordConfigured" to (passwordHash != null))
    }

    private fun ApplicationCall.sessionState(): Map<String, Any?> =
        mapOf(
            "loggedIn" to sessions.isValid(sessionToken()),
            "passwordConfigured" to (passwordHash != null),
        )

    /** 守る対象のフィールドがまだ無いので呼ばれていない。足すときにここを通す */
    @Suppress("unused")
    fun <T> requireLoggedIn(
        call: ApplicationCall,
        block: () -> T,
    ): T {
        if (!sessions.isValid(call.sessionToken())) {
            throw AdminNotLoggedInException()
        }
        return block()
    }

    private companion object {
        /** `admin` の下に進むためだけの値。フィールドは個別に結線してある */
        val NAMESPACE: Map<String, Any?> = emptyMap()

        // Java の enum にすると graphql-java がリフレクションで対応付けて native で壊れる
        const val FAILURE_WRONG_PASSWORD = "WRONG_PASSWORD"
        const val FAILURE_NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

/** graphql-java が捕まえて `errors` に入れる。HTTP は 200 のまま */
class AdminNotLoggedInException : RuntimeException("ログインしていない")
