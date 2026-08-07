package dev.matsudamper.mastodonrss

import dev.matsudamper.mastodonrss.actor.ActorKey
import dev.matsudamper.mastodonrss.crypto.RsaKeys

/**
 * テストで使い回すアクターの鍵。
 *
 * 2048bit の鍵生成はテストごとに走らせると無視できない時間になるので、
 * 中身を問わないテストはこれを共有する。
 */
object TestActorKey {
    val value: ActorKey by lazy {
        ActorKey(
            privateKey = RsaKeys.generateKeyPair().private,
            origin = ActorKey.Origin.Environment,
        )
    }
}
