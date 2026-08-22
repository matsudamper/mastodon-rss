package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorDirectory // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.ActorKey // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.ActorPrivateKey // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.ActorUrls // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.HttpRemoteActors // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.RemoteActors // pragma: allowlist secret
import net.matsudamper.mastodon.rss.actor.StoredActorNames // pragma: allowlist secret
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery // pragma: allowlist secret
import net.matsudamper.mastodon.rss.delivery.HttpActivityDelivery // pragma: allowlist secret
import net.matsudamper.mastodon.rss.feed.FeedFetchService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.follower.FollowerStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.inbox.InboxService // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.RepositoryFollowerStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.logic.RepositoryNoteStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.note.NotePublisher // pragma: allowlist secret
import net.matsudamper.mastodon.rss.note.NoteStore // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.DatabaseConfig // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.Repositories // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.createRepositories // pragma: allowlist secret

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
    val feedFetcher: FeedFetchService = FeedFetchService(),
    val adminSessionStore: AdminSessionInMemoryStore = AdminSessionInMemoryStore(),
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
            feedFetcher.close()
        } finally {
            try {
                (delivery as? AutoCloseable)?.close()
            } finally {
                try {
                    (remoteActors as? AutoCloseable)?.close()
                } finally {
                    repositories.close()
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
        fun create(env: ServerEnv): AppDependencies {
            val repositories = createRepositories(DatabaseConfig(path = env.dbPath))

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
                val remoteActors = HttpRemoteActors()

                val delivery =
                    runCatching { HttpActivityDelivery(actorKey) }
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
                )
            }.getOrElse { failure ->
                repositories.close()
                throw failure
            }
        }
    }
}
