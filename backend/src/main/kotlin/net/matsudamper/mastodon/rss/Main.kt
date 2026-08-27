package net.matsudamper.mastodon.rss

import java.nio.file.Path
import io.ktor.server.application.Application
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.actorRoutes
import net.matsudamper.mastodon.rss.follower.followerRoutes
import net.matsudamper.mastodon.rss.graphql.DiContainer
import net.matsudamper.mastodon.rss.graphql.GraphQlContext
import net.matsudamper.mastodon.rss.graphql.GraphQlEngine
import net.matsudamper.mastodon.rss.graphql.graphQlRoutes
import net.matsudamper.mastodon.rss.graphql.resolver.AccountNoteResolverImpl
import net.matsudamper.mastodon.rss.graphql.resolver.AdminAccountResolverImpl
import net.matsudamper.mastodon.rss.graphql.resolver.AdminMutationResolverImpl
import net.matsudamper.mastodon.rss.graphql.resolver.AdminQueryResolverImpl
import net.matsudamper.mastodon.rss.graphql.resolver.MutationResolverImpl
import net.matsudamper.mastodon.rss.graphql.resolver.QueryResolverImpl
import net.matsudamper.mastodon.rss.inbox.inboxRoutes
import net.matsudamper.mastodon.rss.json.respondJson
import net.matsudamper.mastodon.rss.nodeinfo.nodeInfoRoutes
import net.matsudamper.mastodon.rss.note.featuredRoutes
import net.matsudamper.mastodon.rss.note.noteRoutes
import net.matsudamper.mastodon.rss.note.outboxRoutes
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles
import net.matsudamper.mastodon.rss.staticfiles.staticRoutes
import net.matsudamper.mastodon.rss.telemetry.OpenTelemetryInitializer
import net.matsudamper.mastodon.rss.webfinger.webFingerRoutes

fun main() {
    // 環境変数を読むのはここだけ。以降は引数で配る。
    // DOMAIN が無ければこの時点で落ちる。サーバーを立てる前に止めたいので順番を変えないこと
    val env = ServerEnv()

    val telemetry = OpenTelemetryInitializer.start()
    val deps = AppDependencies.create(env, telemetry = telemetry)
    val server = embeddedServer(CIO, port = env.port, host = env.host) {
        module(deps)
    }

    // 終了処理を `use` に任せない。docker stop で来る SIGTERM では main が返らないまま
    // JVM が終わるので finally まで届かず、閉じずに終わると書き込みが DB のファイルに
    // 確定しない。JVM が終わる経路は必ずシャットダウンフックを通るため、
    // start が例外で終わった場合も含めてここ 1 か所で閉じられる
    Runtime.getRuntime().addShutdownHook(
        Thread {
            // 処理中のリクエストが DB を触っている最中に閉じないよう、先にサーバーを止める。
            // 待ち時間は docker stop の既定の猶予（10 秒）に収まる範囲にする
            server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
            deps.close()
        },
    )

    // フィードの定期取得は、受け付けが始まってから動かす。投稿を受け取った相手は
    // その場で Note やアクターの URL を引きに来るので、待ち受ける前に投稿すると
    // 相手は繋げずに終わる。止めるのは deps.close()
    server.monitor.subscribe(ServerReady) {
        deps.startFeedPolling()
    }

    // start(wait = true) は停止まで返ってこない
    server.start(wait = true)
}

/**
 * ルーティングを組み立てて、運用で見たいものを起動ログに出す。
 *
 * @param deps 使うものは全て [AppDependencies] から取る。本番の組み立ては
 *   [AppDependencies.create]、テストはフェイクを詰めたものを渡す
 */
fun Application.module(deps: AppDependencies) {
    val env = deps.env
    val actorKey = deps.actorKey

    // 書けない DB を抱えたまま起動すると、最初のリクエストまで問題に気付けない。
    // native バイナリでは SQLite のネイティブライブラリ周りで起きやすいので起動時に確かめる
    deps.repositories.verifyWritable()

    // ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
    // 取り違えたまま気付かないのが一番まずいので、起動時に必ず見えるところに出す
    log.info("ドメイン: ${env.domain}")

    // 追加したはずのアカウントに応答しないとき、DB を見ているかどうかがここで切り分けられる
    log.info("アカウント: ${deps.repositories.accounts.list().size} 件")

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

    logAdminLogin(env)

    val diContainer = DiContainer(
        passwordHash = env.adminPasswordHash,
        accountRepository = deps.repositories.accounts,
        followerRepository = deps.repositories.followers,
        domain = env.domain,
        actorDirectory = deps.directory,
        notePublisher = deps.notePublisher,
        noteStore = deps.noteStore,
        feedService = deps.feedService,
    )

    val graphQl = GraphQlEngine.create(
        resolvers = listOf(
            QueryResolverImpl(),
            AccountNoteResolverImpl(),
            MutationResolverImpl(),
            AdminQueryResolverImpl(),
            AdminAccountResolverImpl(),
            AdminMutationResolverImpl(),
        ),
        createContext = { call ->
            GraphQlContext(
                call = call,
                sessionStore = deps.adminSessionStore,
                cookieSecure = env.adminCookieSecure,
            )
        },
        diContainer = diContainer,
        openTelemetry = deps.openTelemetry,
    )

    // ContentNegotiation は入れていない。serializer をリフレクションで引く実装のため
    // native-image で解決できず 500 になる。詳細は json/JsonResponse.kt を参照
    deps.openTelemetry?.let { openTelemetry ->
        install(KtorServerTelemetry) {
            setOpenTelemetry(openTelemetry)
        }
    }

    routing {
        get("/healthz") {
            call.respondJson(HealthResponse.serializer(), HealthResponse(status = "ok"))
        }

        // Mastodon はこの 2 つを WebFinger → Actor の順に引いてアカウントを見つける
        webFingerRoutes(deps.directory)
        actorRoutes(deps.directory, actorKey)

        // 見つけた後、フォローなどのアクティビティはここに POST されてくる
        inboxRoutes(directory = deps.directory, service = deps.inboxService)

        followerRoutes(deps.directory, deps.followerStore)
        outboxRoutes(deps.directory, deps.noteStore)
        featuredRoutes(deps.directory)
        noteRoutes(env.domain, deps.noteStore)

        nodeInfoRoutes(env.domain)

        graphQlRoutes(graphQl)

        // 残り全部を受けるので最後に置く
        staticRoutes(staticFiles)
    }
}

private fun Application.logAdminLogin(env: ServerEnv) {
    if (env.adminPasswordHash == null) {
        log.warn("ADMIN_PASSWORD_HASH が未設定なので管理画面にログインできない")
        return
    }

    if (env.adminCookieSecure) {
        log.info("管理画面のログインを受け付ける。セッション Cookie には Secure を付ける")
    } else {
        log.warn(
            "管理画面のログインを受け付ける。ADMIN_COOKIE_SECURE=false なので" +
                "セッション Cookie に Secure を付けない。http で試すとき以外は外すこと",
        )
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
