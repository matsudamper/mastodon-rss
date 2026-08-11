package net.matsudamper.mastodon.rss.admin

import graphql.schema.idl.RuntimeWiring
import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.applicationCall
import net.matsudamper.mastodon.rss.graphql.GraphQlWiring

/**
 * `session` と `login` はログインしていなくても叩ける。
 * ログインした人だけが叩けるものを足すときは [requireLoggedIn] を通す。
 */
class AdminGraphQl(
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
    private val cookieSecure: Boolean,
) : GraphQlWiring {
    override fun contribute(builder: RuntimeWiring.Builder) {
        builder.type("Query") { it.dataFetcher("admin", { NAMESPACE }) }
        builder.type("Mutation") { it.dataFetcher("admin", { NAMESPACE }) }

        builder.type("AdminQuery") {
            it.dataFetcher("session", { env -> env.applicationCall().sessionState() })
        }

        builder.type("AdminMutation") {
            it
                .dataFetcher("login", { env ->
                    val password = requireNotNull(env.getArgument<String>("password")) { "password が無い" }
                    login(env.applicationCall(), password)
                })
                .dataFetcher("logout", { env -> logout(env.applicationCall()) })
        }
    }

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

    private fun ApplicationCall.sessionState(): Map<String, Any?> {
        return mapOf(
            "loggedIn" to sessions.isValid(sessionToken()),
            "passwordConfigured" to (passwordHash != null),
        )
    }

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
        /** `admin` の下に進むためだけの値。null を返すと non-null 違反になる */
        val NAMESPACE: Map<String, Any?> = emptyMap()

        // Java の enum にすると graphql-java がリフレクションで対応付けて native で壊れる
        const val FAILURE_WRONG_PASSWORD = "WRONG_PASSWORD"
        const val FAILURE_NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

class AdminNotLoggedInException : RuntimeException("ログインしていない")
