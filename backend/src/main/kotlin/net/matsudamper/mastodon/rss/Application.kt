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
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.ActorUsername
import net.matsudamper.mastodon.rss.actor.actorRoutes
import net.matsudamper.mastodon.rss.json.respondJson
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.createRepositories
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles
import net.matsudamper.mastodon.rss.staticfiles.staticRoutes
import net.matsudamper.mastodon.rss.webfinger.webFingerRoutes
import java.nio.file.Path

fun main() {
    // 環境変数を読むのはここだけ。以降は引数で配る
    val env = ServerEnv.fromEnvironment()

    // 鍵が用意できないなら起動しても意味が無いので、サーバーを立てる前に読む
    val actorKey = ActorKeyLoader.load(env.actorPrivateKey)

    // サーバーが止まったら接続も閉じる。start(wait = true) は停止まで返ってこない
    createRepositories(DatabaseConfig(path = env.dbPath)).use { repositories ->
        embeddedServer(CIO, port = env.port, host = env.host) {
            module(repositories, actorKey, env)
        }.start(wait = true)
    }
}

fun Application.module(
    repositories: Repositories,
    actorKey: ActorKey,
    env: ServerEnv,
) {
    // 書けない DB を抱えたまま起動すると、最初のリクエストまで問題に気付けない。
    // native バイナリでは SQLite のネイティブライブラリ周りで起きやすいので起動時に確かめる
    repositories.verifyWritable()

    // ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
    // 取り違えたまま気付かないのが一番まずいので、起動時に必ず見えるところに出す
    val actorUrls = ActorUrls(domain = env.domain, username = env.actorUsername)
    log.info("アクター: ${actorUrls.acct} → ${actorUrls.actorId}")

    // 検証用の使い捨てアクター。Mastodon はリモートアクターを永続キャッシュするので、
    // 名前を変えながら試せる口が無いと、一度間違えたときに直す手段が無くなる
    log.info("動作確認用に acct:${ActorUsername.TEST_PREFIX}<任意>@${env.domain} も応答する")

    val directory = ActorDirectory(actorUrls)

    // 鍵が入れ替わると相手側は署名検証に失敗し続ける。
    // どこから読んだ鍵なのかが後から追えるよう、取得元を必ず出す
    when (val origin = actorKey.origin) {
        is ActorKey.Origin.Environment -> {
            log.info("アクターの秘密鍵: ACTOR_PRIVATE_KEY_PEM から読んだ")
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

    // 画面が出ないときに理由を追えるよう、配信元を起動時に必ず出す。
    // 黙って 404 になると、設定し忘れなのか置き忘れなのかが分からない
    val staticFiles = resolveStaticFiles(env.staticSrcDir)

    // ContentNegotiation は入れていない。serializer をリフレクションで引く実装のため
    // native-image で解決できず 500 になる。詳細は json/JsonResponse.kt を参照
    routing {
        get("/healthz") {
            call.respondJson(HealthResponse.serializer(), HealthResponse(status = "ok"))
        }

        // Mastodon はこの 2 つを WebFinger → Actor の順に引いてアカウントを見つける
        webFingerRoutes(directory)
        actorRoutes(directory, actorKey)

        // 残り全部を受けるので最後に置く
        staticRoutes(staticFiles)
    }
}

/**
 * 静的ファイルの配信元を決めて、その結果を起動ログに出す。
 *
 * 配信できないときは null を返す。この場合 root は 404 になる。
 */
private fun Application.resolveStaticFiles(srcDir: Path?): StaticFiles? {
    if (srcDir == null) {
        log.info(
            "STATIC_SRC_DIR が未設定なので静的ファイルを配信しない。" +
                "管理画面を出すには :frontend の成果物を置いたディレクトリを指定する",
        )
        return null
    }

    val staticFiles = StaticFiles(srcDir)
    if (!staticFiles.isAvailable()) {
        log.warn("${staticFiles.root} が無いので静的ファイルを配信しない")
        return null
    }

    log.info("静的ファイルを ${staticFiles.root} から配信する")
    return staticFiles
}
