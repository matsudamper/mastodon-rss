package net.matsudamper.mastodon.rss

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.ActorKeyConfig
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsername
import net.matsudamper.mastodon.rss.actor.actorRoutes
import net.matsudamper.mastodon.rss.admin.AdminConfig
import net.matsudamper.mastodon.rss.admin.AdminSessions
import net.matsudamper.mastodon.rss.admin.adminRoutes
import net.matsudamper.mastodon.rss.admin.api.AdminApiPaths
import net.matsudamper.mastodon.rss.json.respondJson
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.createRepositories
import net.matsudamper.mastodon.rss.webfinger.webFingerRoutes

fun main() {
    val config = ServerConfig.fromEnvironment()

    // 鍵が用意できないなら起動しても意味が無いので、サーバーを立てる前に読む
    val actorKey = ActorKeyLoader.load(ActorKeyConfig.fromEnvironment())

    // ハッシュが壊れていたらここで落ちる。管理画面のハッシュは未設定でもよく、
    // その場合はログインできない状態で起動する（ハッシュを作るための起動）
    val adminConfig = AdminConfig.fromEnvironment()

    // サーバーが止まったら接続も閉じる。start(wait = true) は停止まで返ってこない
    createRepositories(DatabaseConfig.fromEnvironment()).use { repositories ->
        embeddedServer(CIO, port = config.port, host = config.host) {
            module(repositories, actorKey, config, adminConfig)
        }.start(wait = true)
    }
}

fun Application.module(
    repositories: Repositories,
    actorKey: ActorKey,
    config: ServerConfig = ServerConfig.fromEnvironment(),
    adminConfig: AdminConfig = AdminConfig.fromEnvironment(),
) {
    // 書けない DB を抱えたまま起動すると、最初のリクエストまで問題に気付けない。
    // native バイナリでは SQLite のネイティブライブラリ周りで起きやすいので起動時に確かめる
    repositories.verifyWritable()

    // ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
    // 取り違えたまま気付かないのが一番まずいので、起動時に必ず見えるところに出す
    val actorUrls = ActorUrls(domain = config.domain, username = config.actorUsername)
    log.info("アクター: ${actorUrls.acct} → ${actorUrls.actorId}")

    // 検証用の使い捨てアクター。Mastodon はリモートアクターを永続キャッシュするので、
    // 名前を変えながら試せる口が無いと、一度間違えたときに直す手段が無くなる
    log.info("動作確認用に acct:${ActorUsername.TEST_PREFIX}<任意>@${config.domain} も応答する")

    val directory = ActorDirectory(actorUrls)

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

    // ログインできない状態で起動しているのか、設定を書き忘れているのかが
    // 後から分からなくなるので、どちらなのかを起動時に出す
    if (adminConfig.loginConfigured) {
        log.info("管理画面: ${AdminApiPaths.BASE} （ログインあり）")
    } else {
        log.warn(
            "管理画面: ${AdminApiPaths.BASE} は ${AdminConfig.ENV_PASSWORD_HASH} が未設定のためログインできない。" +
                "${AdminApiPaths.PASSWORD_HASH_PAGE} でハッシュを作り、環境変数に入れて起動し直すこと",
        )
    }

    val adminSessions = AdminSessions(adminConfig.sessionTtl)

    // ContentNegotiation は入れていない。serializer をリフレクションで引く実装のため
    // native-image で解決できず 500 になる。詳細は json/JsonResponse.kt を参照
    routing {
        get("/healthz") {
            call.respondJson(HealthResponse.serializer(), HealthResponse(status = "ok"))
        }

        // Mastodon はこの 2 つを WebFinger → Actor の順に引いてアカウントを見つける
        webFingerRoutes(directory)
        actorRoutes(directory, actorKey)

        // 運用者だけが使う画面。外から叩かれてよいものではない
        adminRoutes(adminConfig, adminSessions)
    }
}
