package net.matsudamper.mastodon.rss.admin

import graphql.schema.idl.RuntimeWiring
import io.ktor.server.application.ApplicationCall
import net.matsudamper.mastodon.rss.crypto.PasswordHash
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine.Companion.applicationCall
import net.matsudamper.mastodon.rss.graphql.GraphQlWiring

/**
 * 管理画面のフィールドの結線。
 *
 * `Query.admin` と `Mutation.admin` の下にまとめる。認可はエンドポイントではなく
 * フィールドごとに見るので、ログインの口も同じ `/graphql` に置ける。
 * `session` と `login` はログインしていなくても叩ける。フィードの CRUD のように
 * ログインした人だけが叩けるものを足すときは、[requireLoggedIn] を通す。
 *
 * 返す値は全て `Map`。データクラスを返すと graphql-java が
 * `PropertyDataFetcher` のリフレクションで読みに行き、JVM では動いて native
 * バイナリでだけ全フィールドが null になる。
 *
 * 総当たり対策（試行回数の制限）はまだ無い。Phase 7 で入れる。
 *
 * @param passwordHash `ADMIN_PASSWORD_HASH` から読んだもの。未設定なら null で、
 *   この場合はログインできない。設定前でも画面は開けるようにするため起動は通す
 * @param cookieSecure セッション Cookie に `Secure` を付けるか
 */
class AdminGraphQl(
    private val passwordHash: PasswordHash?,
    private val sessions: AdminSessions,
    private val cookieSecure: Boolean,
) : GraphQlWiring {
    override fun contribute(builder: RuntimeWiring.Builder) {
        // admin は入れ物でしかないので、中身の無い値を返して下のフィールドに進ませる。
        // null を返すと non-null 違反になって、下まで到達しない
        builder.type("Query") { it.dataFetcher("admin", { NAMESPACE }) }
        builder.type("Mutation") { it.dataFetcher("admin", { NAMESPACE }) }

        builder.type("AdminQuery") {
            it.dataFetcher("session", { env -> env.applicationCall().sessionState() })
        }

        builder.type("AdminMutation") {
            it
                .dataFetcher("login", { env ->
                    // 引数はスキーマで String! なので、graphql-java が検証を通した時点で必ず入っている
                    val password = requireNotNull(env.getArgument<String>("password")) { "password が無い" }
                    login(env.applicationCall(), password)
                })
                .dataFetcher("logout", { env -> logout(env.applicationCall()) })
        }
    }

    /**
     * パスワードを照合して、通ればセッションを発行する。
     *
     * 失敗の理由は分けて返す。ハッシュが未設定なのを「パスワードが違う」と同じに
     * 見せると、何を直せばよいのか画面からは分からない。
     */
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

        // 発行したばかりの Cookie はまだリクエスト側に無いので、
        // sessionState() を呼ぶと未ログインに見える。ここは通った事実をそのまま返す
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

    /**
     * ログインしている人だけが通れるようにする。
     *
     * まだ守る対象のフィールドが無いので呼ばれていない。フィードの CRUD を
     * 足すときにここを通す。認可を各フィールドの中に書くと、書き忘れたものが
     * 素通りする。
     */
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
        /** `admin` の下に進むためだけの値。フィールドは全て個別に結線してあるので中身は要らない */
        val NAMESPACE: Map<String, Any?> = emptyMap()

        // enum は名前の文字列で返す。Java の enum を作ると graphql-java が
        // リフレクションで対応付けることになり、native バイナリで解決できない
        const val FAILURE_WRONG_PASSWORD = "WRONG_PASSWORD"
        const val FAILURE_NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

/**
 * ログインしていない人が、ログインが要るフィールドを引いたとき。
 *
 * graphql-java がこれを捕まえて `errors` に入れる。HTTP は 200 のまま。
 */
class AdminNotLoggedInException : RuntimeException("ログインしていない")
