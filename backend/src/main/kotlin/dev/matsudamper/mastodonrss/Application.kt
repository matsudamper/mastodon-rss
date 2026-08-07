package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes
import dev.matsudamper.mastodonrss.json.AppJson
import dev.matsudamper.mastodonrss.repository.DatabaseConfig
import dev.matsudamper.mastodonrss.repository.Repositories
import dev.matsudamper.mastodonrss.repository.createRepositories
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val config = ServerConfig.fromEnvironment()

    // サーバーが止まったら接続も閉じる。start(wait = true) は停止まで返ってこない
    createRepositories(DatabaseConfig.fromEnvironment()).use { repositories ->
        embeddedServer(CIO, port = config.port, host = config.host) {
            module(repositories, config)
        }.start(wait = true)
    }
}

fun Application.module(repositories: Repositories, config: ServerConfig = ServerConfig.fromEnvironment()) {
    // 書けない DB を抱えたまま起動すると、最初のリクエストまで問題に気付けない。
    // native バイナリでは SQLite のネイティブライブラリ周りで起きやすいので起動時に確かめる
    repositories.verifyWritable()

    // ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
    // 取り違えたまま気付かないのが一番まずいので、起動時に必ず見えるところに出す
    if (config.domain == null) {
        log.warn("DOMAIN が未設定。Phase 1 の WebFinger と Actor はこの値から URL を組み立てる")
    } else {
        log.info("DOMAIN: ${config.domain}")
    }

    install(ContentNegotiation) {
        json(AppJson)

        // Mastodon はアクターを取りに来るときに Accept: application/activity+json を送る。
        // ここで登録しておかないと 406 になり、リモートからアクターを解決できない。
        register(ActivityPubContentTypes.ActivityJson, KotlinxSerializationConverter(AppJson))
        register(ActivityPubContentTypes.LdJson, KotlinxSerializationConverter(AppJson))
        register(ActivityPubContentTypes.JrdJson, KotlinxSerializationConverter(AppJson))
    }

    routing {
        get("/healthz") {
            call.respond(HealthResponse(status = "ok"))
        }
    }
}
