package net.matsudamper.mastodon.rss

import net.matsudamper.mastodon.rss.actor.ActorDirectory
import net.matsudamper.mastodon.rss.actor.ActorKey
import net.matsudamper.mastodon.rss.actor.ActorKeyLoader
import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.actor.HttpRemoteActors
import net.matsudamper.mastodon.rss.actor.RemoteActors
import net.matsudamper.mastodon.rss.admin.AdminSessionInMemoryStore
import net.matsudamper.mastodon.rss.delivery.ActivityDelivery
import net.matsudamper.mastodon.rss.delivery.HttpActivityDelivery
import net.matsudamper.mastodon.rss.httpsignature.HttpSignatureVerifier
import net.matsudamper.mastodon.rss.inbox.FollowHandler
import net.matsudamper.mastodon.rss.inbox.InboxService
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
) : AutoCloseable {
    /**
     * ドメインはアクター ID に焼き込まれ、Mastodon 側にキャッシュされると後から変えられない。
     * 綴りが 1 か所だけ違う状態を作らないよう、組み立てはここに通す。
     */
    val actorUrls: ActorUrls = ActorUrls(domain = env.domain, username = env.actorUsername)

    val directory: ActorDirectory = ActorDirectory(actorUrls)

    /**
     * inbox が受け取ったアクティビティの検証と振り分け。
     * 種類ごとの処理はハンドラを足す形になっていて、Phase 3 の `Undo` と `Delete` はここに並ぶ。
     */
    val inboxService: InboxService =
        InboxService(
            verifier = HttpSignatureVerifier(remoteActors),
            handlers = listOf(FollowHandler(remoteActors, delivery)),
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
                repositories.close()
            }
        }
    }

    companion object {
        /**
         * 本番の組み立て。
         *
         * 鍵は DB より先に読む。鍵を用意できないならサーバーを立てても意味が無いので、
         * 先に落とすため。DB を開いた後に失敗した場合は、開いた分を閉じてから投げ直す。
         */
        fun create(env: ServerEnv): AppDependencies {
            val actorKey = ActorKeyLoader.load(env.actorPrivateKey)

            val repositories = createRepositories(DatabaseConfig(path = env.dbPath))

            // ここから先で失敗すると、開いた DB が閉じられないまま起動が止まる
            return runCatching {
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
