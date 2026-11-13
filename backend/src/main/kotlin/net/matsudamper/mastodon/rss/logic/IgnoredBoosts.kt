package net.matsudamper.mastodon.rss.logic

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import net.matsudamper.kotpub.boost.BoostStore
import net.matsudamper.kotpub.note.OutboxBoostStore
import net.matsudamper.kotpub.note.OutboxPosition
import net.matsudamper.kotpub.note.RemoteNoteLookup
import net.matsudamper.kotpub.note.RemoteNotes
import net.matsudamper.kotpub.note.StoredBoost

/**
 * ブースト（`Announce`）は受け取っても記録せず、こちらからもブーストしない。
 *
 * inbox の span の outcome は kotpub が「記録しなかった」「投稿が無い」と付けるので、
 * 記録する仕組みが無いせいだと分かるよう [IGNORED] を足す。
 */
internal object IgnoredBoosts {
    private val IGNORED: AttributeKey<String> = AttributeKey.stringKey("activitypub.boost.ignored")

    val store: BoostStore = object : BoostStore {
        override fun add(boost: BoostStore.ReceivedBoost): Boolean {
            Span.current().setAttribute(IGNORED, "local_boost")
            return false
        }

        override suspend fun saveRemote(boost: BoostStore.ReceivedRemoteBoost) {
            Span.current().setAttribute(IGNORED, "remote_boost")
        }

        override fun remove(
            actorUri: String,
            activityUri: String,
        ): Boolean = false

        override fun removeActor(actorUri: String): Int = 0

        override fun findPublicKeyPem(actorUri: String): String? = null
    }

    /**
     * 他のサーバーの投稿のブーストは記録しないので、ブーストされた投稿を取りに行かない
     */
    val remoteNotes: RemoteNotes = RemoteNotes {
        Span.current().setAttribute(IGNORED, "remote_note_lookup")
        RemoteNoteLookup.NotFound
    }

    val outbox: OutboxBoostStore = object : OutboxBoostStore {
        override fun list(
            username: String,
            after: OutboxPosition?,
            limit: Int,
        ): List<StoredBoost> = listOf()

        override fun count(username: String): Long = 0
    }
}
