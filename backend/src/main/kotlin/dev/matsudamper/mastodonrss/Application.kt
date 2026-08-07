package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.actor.ActorKey
import dev.matsudamper.mastodonrss.actor.ActorKeyConfig
import dev.matsudamper.mastodonrss.actor.ActorKeyLoader
import dev.matsudamper.mastodonrss.json.respondJson
import dev.matsudamper.mastodonrss.repository.DatabaseConfig
import dev.matsudamper.mastodonrss.repository.Repositories
import dev.matsudamper.mastodonrss.repository.createRepositories
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val config = ServerConfig.fromEnvironment()

    // 鍵が用意できないなら起動しても意味が無いので、サーバーを立てる前に読む
    val actorKey = ActorKeyLoader.load(ActorKeyConfig.fromEnvironment())

    // サーバーが止まったら接続も閉じる。start(wait = true) は停止まで返ってこない
    createRepositories(DatabaseConfig.fromEnvironment()).use { repositories ->
        embeddedServer(CIO, port = config.port, host = config.host) {
            module(repositories, actorKey, config)
        }.start(wait = true)
    }
}

fun Application.module(
    repositories: Repositories,
    actorKey: ActorKey,
    config: ServerConfig = ServerConfig.fromEnvironment(),
) {
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

    // 鍵が入れ替わると相手側は署名検証に失敗し続ける。
    // どこから読んだ鍵なのかが後から追えるよう、取得元を必ず出す
    when (val origin = actorKey.origin) {
        is ActorKey.Origin.Environment -> {
            log.info("アクターの秘密鍵: ${ActorKeyConfig.ENV_PRIVATE_KEY_PEM} から読んだ")
        }

        is ActorKey.Origin.LoadedFile -> {
            log.info("アクターの秘密鍵: ${origin.path} から読んだ")
        }

        is ActorKey.Origin.GeneratedFile -> {
            log.warn(
                "アクターの秘密鍵を新しく生成して ${origin.path} に書き出した。" +
                    "既にフォロワーがいる状態でこれが出たら、以前の鍵を失っている",
            )
        }
    }

    // ContentNegotiation は入れていない。serializer をリフレクションで引く実装のため
    // native-image で解決できず 500 になる。詳細は json/JsonResponse.kt を参照
    routing {
        get("/healthz") {
            call.respondJson(HealthResponse.serializer(), HealthResponse(status = "ok"))
        }
    }
}
