package net.matsudamper.mastodon.rss.logic

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import net.matsudamper.kotpub.note.OutboxBoostStore
import net.matsudamper.kotpub.note.OutboxPosition
import net.matsudamper.kotpub.note.RemoteNoteLookup
import net.matsudamper.kotpub.note.RemoteNotes
import net.matsudamper.kotpub.note.StoredBoost

/**
 * 他のサーバーの投稿のブーストは受け取っても記録せず、こちらからもブーストしない。
 *
 * inbox の span の outcome は kotpub が「投稿が無い」などと付けるので、
 * 記録する仕組みが無いせいだと分かるよう [IGNORED] を足す。
 */
internal object IgnoredBoosts {
    private val IGNORED: AttributeKey<String> = AttributeKey.stringKey("activitypub.boost.ignored")

    /**
     * ブーストされた投稿を記録しないので、取りに行かない
     */
    val remoteNotes: RemoteNotes = RemoteNotes {
        markIgnored("remote_note_lookup")
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

    fun markIgnored(kind: String) {
        Span.current().setAttribute(IGNORED, kind)
    }
}
