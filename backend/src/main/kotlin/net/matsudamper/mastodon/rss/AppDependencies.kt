package net.matsudamper.mastodon.rss

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader
import net.matsudamper.mastodon.rss.actor.ActorPrivateKey
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.HttpRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.actor.StoredActorNames
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.HttpActivityDelivery
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.inbox.InboxService
import net.matsudamper.mastodon.rss.logic.RepositoryFollowerStore
import net.matsudamper.mastodon.rss.logic.RepositoryNoteStore
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.createRepositories

/**
 * アプリが使うものを作って配る場所。
 *
 * 何をどの順で作り、どの順で閉じるかをここ 1 か所に集める。以前は [main] の中で
 * `use` を入れ子にしていたが、抱えるものが増えるたびに入れ子が深くなり、
 * [Application.module] の引数も一緒に伸びていく形だった。Phase 4 の配信キューと
 * Phase 5 のスケジューラはどちらもここに並ぶ。
 *
 * 外から作れるようにしてあるのはテストのため。フェイクを渡せば、
 * 本物の DB や外向きの HTTP を用意せずにルーティングを組み立てられる。
 * 本番の組み立ては [create] にある。
 *
 * @param remoteActors 相手のアクターの引き先。署名検証に使う公開鍵と、
 *   `Accept` の宛先になる inbox をここから取る。本番は [HttpRemoteActors] が
 *   相手のサーバーに GET しに行く
 * @param delivery こちらから相手の inbox に POST する口
 */
class AppDependencies(
    val repositories: Repositories,
    val actorKey: ActorKey,
    val env: ServerEnv,
    val remoteActors: RemoteActors,
    val delivery: ActivityDelivery,
    val adminSessionStore: AdminSessionInMemoryStore = AdminSessionInMemoryStore(),
    val openTelemetry: OpenTelemetry? = null,
    private val telemetry: OpenTelemetryHandle? = null,
) : AutoCloseable {
    /**
     * ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
     * 綴りが 1 か所だけ違う状態を作らないよう、組み立てはここに通す。
     */
    val actorUrls: ActorUrls = ActorUrls(domain = env.domain, username = env.actorUsername)

    val followerStore: FollowerStore = RepositoryFollowerStore(repositories.followers)

    val noteStore: NoteStore = RepositoryNoteStore(repositories.notes)

    // 毎回引き直す。持ち回すと、追加したアカウントが引けるようになるまで間が空く
    val directory: ActorDirectory = ActorDirectory(
        fixed = actorUrls,
        stored = object : StoredActorNames {
            override fun find(username: String): String? {
                return repositories.accounts.findByUsername(username)?.username
            }

            override fun finds(usernames: Set<String>): Map<String, String> {
                return repositories.accounts.findByUsernames(usernames).mapValues { it.value.username }
            }
        },
    )

    /**
     * inbox が受け取ったアクティビティの検証と振り分け。
     *
     * 何をどう組み合わせるかは ActivityPub 側の話なので
     * [InboxService.default] に任せる。ここで決めるのは、その材料になる
     * [remoteActors] と [delivery] を本番のものにするかフェイクにするかだけ。
     */
    val inboxService: InboxService = InboxService.default(
        remoteActors = remoteActors,
        delivery = delivery,
        followers = followerStore,
    )

    val notePublisher: NotePublisher = NotePublisher(
        notes = noteStore,
        followers = followerStore,
        delivery = delivery,
    )

    /**
     * 抱えているものを作った順の逆に閉じる。
     *
     * 途中で例外が出ても残りを閉じられるよう finally で繋ぐ。並べて呼ぶだけだと、
     * 最初の close が投げた時点で後ろが開いたままになる。
     */
    override fun close() {
        try {
            (delivery as? AutoCloseable)?.close()
        } finally {
            try {
                (remoteActors as? AutoCloseable)?.close()
            } finally {
                try {
                    repositories.close()
                } finally {
                    telemetry?.close()
                }
            }
        }
    }

    companion object {
        /**
         * 本番の組み立て。
         *
         * DB を先に開く。鍵のファイルが無いときに新しく作ってよいかどうかが、
         * フォロワーが記録されているかどうかで決まるため。
         * 開いた後に失敗した場合は、開いた分を閉じてから投げ直す。
         */
        fun create(
            env: ServerEnv,
            telemetry: OpenTelemetryHandle? = null,
        ): AppDependencies {
            val repositories = createRepositories(DatabaseConfig(path = env.dbPath))
            val openTelemetry = telemetry?.openTelemetry

            // ここから先で失敗すると、開いた DB が閉じられないまま起動が止まる
            return runCatching {
                val loadActorKey = ActorKeyLoader.load(env.actorPrivateKey)
                if (loadActorKey == null && repositories.followers.hasAny()) {
                    throw IllegalStateException(
                        "フォロワーが記録されているのにアクターの秘密鍵が無い。" +
                            "鍵を失った状態で新しい鍵を作ると既存のフォロワーから見て別人になるため起動しない。" +
                            "以前の鍵を ACTOR_PRIVATE_KEY_PATH に戻すこと",
                    )
                }
                val actorKey = loadActorKey ?: when (env.actorPrivateKey) {
                    is ActorPrivateKey.Pem -> throw IllegalStateException()
                    is ActorPrivateKey.File -> ActorKeyLoader.create(env.actorPrivateKey)
                }

                // 相手のアクターを引くのと、こちらから送るのとで外向きの HTTP を張る。
                // どちらも接続を抱えるので、サーバーの外側で開いて確実に閉じる
                val remoteActors = HttpRemoteActors(openTelemetry = openTelemetry)

                val delivery =
                    runCatching { HttpActivityDelivery(actorKey, openTelemetry = openTelemetry) }
                        .getOrElse { failure ->
                            remoteActors.close()
                            throw failure
                        }

                AppDependencies(
                    repositories = repositories,
                    actorKey = actorKey,
                    env = env,
                    remoteActors = remoteActors,
                    delivery = delivery,
                    openTelemetry = openTelemetry,
                    telemetry = telemetry,
                )
            }.getOrElse { failure ->
                repositories.close()
                throw failure
            }
        }
    }
}

/**
 * OpenTelemetry SDK を手動で起動する。
 *
 * GraalVM native-image では javaagent が使えないため、
 * [AutoConfiguredOpenTelemetrySdk] で環境変数から設定を読む。
 * ログは対応しない。トレースとメトリクスのみ。
 */
class OpenTelemetryHandle(
    val openTelemetry: OpenTelemetry,
    private val sdk: OpenTelemetrySdk,
) : AutoCloseable {
    override fun close() {
        sdk.close()
    }
}

object OpenTelemetryBootstrap {
    private const val SERVICE_NAME = "mastodon-rss"

    private val ENV_TO_PROPERTY =
        mapOf(
            "OTEL_TRACES_EXPORTER" to "otel.traces.exporter",
            "OTEL_METRICS_EXPORTER" to "otel.metrics.exporter",
            "OTEL_LOGS_EXPORTER" to "otel.logs.exporter",
            "OTEL_EXPORTER_OTLP_ENDPOINT" to "otel.exporter.otlp.endpoint",
            "OTEL_EXPORTER_OTLP_PROTOCOL" to "otel.exporter.otlp.protocol",
            "OTEL_SERVICE_NAME" to "otel.service.name",
        )

    /**
     * 環境変数が無いときはエクスポートしない。
     * 未設定のまま OTLP へ送ろうとすると、コレクターが無い環境で
     * 接続エラーが出続ける。
     */
    fun start(env: Map<String, String> = System.getenv()): OpenTelemetryHandle? {
        if (isDisabled(env)) return null
        if (!isExportEnabled(env)) return null

        val builder =
            AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addResourceCustomizer { oldResource, _ ->
                    if (oldResource.getAttribute(ServiceAttributes.SERVICE_NAME) != null) {
                        oldResource
                    } else {
                        oldResource.toBuilder()
                            .put(ServiceAttributes.SERVICE_NAME, SERVICE_NAME)
                            .build()
                    }
                }
                .addPropertiesCustomizer { config ->
                    buildMap {
                        if (config.getString("otel.traces.exporter") == null) {
                            put("otel.traces.exporter", "none")
                        }
                        if (config.getString("otel.metrics.exporter") == null) {
                            put("otel.metrics.exporter", "none")
                        }
                        if (config.getString("otel.logs.exporter") == null) {
                            put("otel.logs.exporter", "none")
                        }
                    }
                }

        if (env !== System.getenv()) {
            builder.addPropertiesSupplier { otelPropertiesFromEnv(env) }
        }

        val sdk = builder.build()

        return OpenTelemetryHandle(
            openTelemetry = sdk.openTelemetrySdk,
            sdk = sdk.openTelemetrySdk,
        )
    }

    private fun isDisabled(env: Map<String, String>): Boolean =
        env["OTEL_SDK_DISABLED"]?.trim()?.lowercase() == "true"

    private fun isExportEnabled(env: Map<String, String>): Boolean {
        if (!env["OTEL_EXPORTER_OTLP_ENDPOINT"].isNullOrBlank()) return true

        return listOf(
            exporterValue(env, "OTEL_TRACES_EXPORTER"),
            exporterValue(env, "OTEL_METRICS_EXPORTER"),
        ).any { it != "none" }
    }

    private fun exporterValue(
        env: Map<String, String>,
        envKey: String,
    ): String = env[envKey]?.trim()?.lowercase() ?: "none"

    private fun otelPropertiesFromEnv(env: Map<String, String>): Map<String, String> =
        buildMap {
            for ((envKey, property) in ENV_TO_PROPERTY) {
                val value = env[envKey]?.trim()
                if (!value.isNullOrEmpty()) {
                    put(property, value)
                }
            }
        }
}
