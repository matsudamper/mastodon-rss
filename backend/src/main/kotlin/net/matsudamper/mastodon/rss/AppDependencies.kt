package net.matsudamper.mastodon.rss

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorHeaders
import net.matsudamper.mastodon.rss.actor.ActorIcons
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader
import net.matsudamper.mastodon.rss.actor.ActorPrivateKey
import net.matsudamper.mastodon.rss.actor.ActorPublisher
import net.matsudamper.mastodon.rss.actor.HttpRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.actor.StoredActorNames
import net.matsudamper.mastodon.rss.actor.StoredActorProfiles
import net.matsudamper.mastodon.rss.actor.StoredFeedLinks
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.HttpActivityDelivery
import net.matsudamper.mastodon.rss.feed.FeedFetchService
import net.matsudamper.mastodon.rss.feed.FeedPoller
import net.matsudamper.mastodon.rss.feed.IconFetchService
import net.matsudamper.mastodon.rss.follower.FollowerStore
import net.matsudamper.mastodon.rss.inbox.InboxService
import net.matsudamper.mastodon.rss.logic.AccountIconFiles
import net.matsudamper.mastodon.rss.logic.ActorHeaderService
import net.matsudamper.mastodon.rss.logic.ActorIconService
import net.matsudamper.mastodon.rss.logic.FeedHeaderService
import net.matsudamper.mastodon.rss.logic.FeedHeaders
import net.matsudamper.mastodon.rss.logic.FeedIconService
import net.matsudamper.mastodon.rss.logic.FeedIconStore
import net.matsudamper.mastodon.rss.logic.FeedIcons
import net.matsudamper.mastodon.rss.logic.FeedService
import net.matsudamper.mastodon.rss.logic.RepositoryActorProfiles
import net.matsudamper.mastodon.rss.logic.RepositoryFeedLinks
import net.matsudamper.mastodon.rss.logic.RepositoryFollowerStore
import net.matsudamper.mastodon.rss.logic.RepositoryNoteStore
import net.matsudamper.mastodon.rss.note.NotePublisher
import net.matsudamper.mastodon.rss.note.NoteStore
import net.matsudamper.mastodon.rss.repository.DatabaseConfig
import net.matsudamper.mastodon.rss.repository.Repositories
import net.matsudamper.mastodon.rss.repository.createRepositories
import net.matsudamper.mastodon.rss.staticfiles.StaticFiles
import net.matsudamper.mastodon.rss.telemetry.OpenTelemetryInitializer
import net.matsudamper.mastodon.rss.url.WebPageUrls
import net.matsudamper.mastodon.rss.webpage.DomainWebPageUrls

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
 * @param webPageUrlsOverride 相手に渡す、人が開くページの URL。ActivityPub の `url` に入る。
 *   画面のパスは `:frontend` の都合なので、`:backend:feature-mastodon` には持たせず
 *   ここから渡す。渡さなければ [staticFiles] があるときだけ組み立てる。
 *   [webPageUrls] と名前を分けるのは、同じ名前だとクラス本体の初期化式が
 *   プロパティではなく引数（既定は null）を見てしまい、配信する `Create` にだけ
 *   `url` が入らなくなるため
 */
class AppDependencies(
    val repositories: Repositories,
    val actorKey: ActorKey,
    val env: ServerEnv,
    val remoteActors: RemoteActors,
    val delivery: ActivityDelivery,
    val feedFetcher: FeedFetchService = FeedFetchService(),
    val iconFetcher: IconFetchService = IconFetchService(),
    val adminSessionStore: AdminSessionInMemoryStore = AdminSessionInMemoryStore(),
    val openTelemetry: OpenTelemetry? = null,
    webPageUrlsOverride: WebPageUrls? = null,
    private val telemetry: OpenTelemetryInitializer.Handler? = null,
) : AutoCloseable {
    /**
     * 画面の配信元。無ければ画面は出ない。
     *
     * 指定と、そこに実体があるかの両方を見る。結果を起動ログに出すのは `Main` の側
     */
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    /**
     * 相手に渡す画面の URL。画面を配信しない構成では null になり、`url` を出さない。
     *
     * 画面が無いのに `url` を出すと、相手のプロフィールやパーマリンクが 404 を指す。
     * 出さなければ相手は `id` に倒すので、JSON のパスが開く。
     *
     * ディレクトリではなく `index.html` の有無で決める。アカウントの画面はそれを返して
     * 画面側に解釈させるので、置き忘れたディレクトリを指していると 404 になる
     */
    val webPageUrls: WebPageUrls? =
        webPageUrlsOverride ?: staticFiles?.takeIf { it.index() != null }?.let { DomainWebPageUrls(env.domain) }

    val followerStore: FollowerStore = RepositoryFollowerStore(repositories.followers)

    val noteStore: NoteStore = RepositoryNoteStore(repositories.notes)

    // 毎回引き直す。持ち回すと、追加したアカウントが引けるようになるまで間が空く
    val directory: ActorDirectory = ActorDirectory(
        domain = env.domain,
        stored = object : StoredActorNames {
            override fun find(username: String): String? {
                return repositories.accounts.findByUsername(username)?.username
            }

            override fun finds(usernames: Set<String>): Map<String, String> {
                return repositories.accounts.findByUsernames(usernames).mapValues { it.value.username }
            }
        },
    )

    val feedLinks: StoredFeedLinks = RepositoryFeedLinks(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        headers = repositories.feedHeaders,
    )

    private val feedIconStore: FeedIconStore = FeedIconStore(env.iconCacheDir)

    val actorIcons: ActorIcons = ActorIconService(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        icons = repositories.feedIcons,
        store = feedIconStore,
    )

    val actorHeaders: ActorHeaders = ActorHeaderService(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        headers = repositories.feedHeaders,
        store = feedIconStore,
    )

    private val feedHeaders: FeedHeaders = FeedHeaderService(
        headers = repositories.feedHeaders,
        store = feedIconStore,
        fetcher = iconFetcher,
    )

    private val feedIcons: FeedIcons = FeedIconService(
        icons = repositories.feedIcons,
        store = feedIconStore,
        fetcher = iconFetcher,
    )

    val accountIconFiles: AccountIconFiles = AccountIconFiles(
        feeds = repositories.feeds,
        icons = repositories.feedIcons,
        store = feedIconStore,
    )

    val actorProfiles: StoredActorProfiles = RepositoryActorProfiles(repositories.accounts)

    /**
     * フォロー成立後に過去の投稿を配る間、inbox の応答を待たせないためのスコープ。
     *
     * 配り終える前にプロセスが落ちたら、その分は届かない。フォロー自体は
     * 成立しているので、次の新着からは普通に届く
     */
    private val followBackfillScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        notes = noteStore,
        backfillScope = followBackfillScope,
        webPages = webPageUrls,
    )

    val notePublisher: NotePublisher = NotePublisher(
        notes = noteStore,
        followers = followerStore,
        delivery = delivery,
        webPages = webPageUrls,
    )

    val feedService: FeedService = FeedService(
        accounts = repositories.accounts,
        feeds = repositories.feeds,
        feedItems = repositories.feedItems,
        fetcher = feedFetcher,
        actorDirectory = directory,
        notePublisher = notePublisher,
        icons = feedIcons,
        headers = feedHeaders,
    )

    private val feedPollingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * フィードの定期ポーリングを始める。
     *
     * 呼ぶまで動かない。止めるのは [stopFeedPolling]
     */
    fun startFeedPolling() {
        FeedPoller(feedService).start(feedPollingScope)
    }

    /**
     * 定期ポーリングを止めて、走っている取り込みが終わるまで待つ。
     *
     * 待ち受けを止める前に呼ぶ。投稿を受け取った相手はその場で Note やアクターの
     * URL を引きに来るので、止めた後に投稿すると相手は繋げずに終わる。
     * 何度呼んでもよい。待ち時間は docker stop の既定の猶予（10 秒）に収まる範囲にする
     */
    fun stopFeedPolling() {
        runBlocking {
            withTimeoutOrNull(3_000) {
                feedPollingScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    /**
     * 走っている過去の投稿の配信を止めて、終わるまで待つ。
     *
     * 配信は DB と HTTP クライアントを使うので、閉じる前に止める。
     * 途中で切れた分は届かないが、フォロー自体は成立しているので次の新着からは届く。
     * 待ち時間は [stopFeedPolling] と同じ理由で短く切る
     */
    private fun stopFollowBackfill() {
        runBlocking {
            withTimeoutOrNull(3_000) {
                followBackfillScope.coroutineContext.job.cancelAndJoin()
            }
        }
    }

    val actorPublisher: ActorPublisher = ActorPublisher(
        notes = noteStore,
        followers = followerStore,
        delivery = delivery,
        actorKey = actorKey,
        feedLinks = feedLinks,
        profiles = actorProfiles,
        webPages = webPageUrls,
    )

    /**
     * 抱えているものを作った順の逆に閉じる。
     *
     * 1 つが投げても残りは閉じ切る。並べて呼ぶだけだと、最初の close が投げた時点で
     * 後ろが開いたままになる。投げられたものは最初の 1 つにまとめて上げ直す。
     */
    override fun close() {
        // 取り込みの途中で DB や HTTP クライアントを閉じないよう、先に止めて終わるまで待つ
        stopFeedPolling()
        stopFollowBackfill()

        val failures = listOf<() -> Unit>(
            { feedFetcher.close() },
            { iconFetcher.close() },
            { delivery.close() },
            { remoteActors.close() },
            { repositories.close() },
            { telemetry?.close() },
        ).mapNotNull { close -> runCatching(close).exceptionOrNull() }

        val failure = failures.firstOrNull() ?: return
        failures.drop(1).forEach { failure.addSuppressed(it) }
        throw failure
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
            telemetry: OpenTelemetryInitializer.Handler? = null,
        ): AppDependencies {
            val repositories = createRepositories(DatabaseConfig(path = env.dbPath), openTelemetry = telemetry?.openTelemetry)
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
