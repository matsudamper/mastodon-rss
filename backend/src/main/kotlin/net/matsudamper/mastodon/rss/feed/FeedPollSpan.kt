package net.matsudamper.mastodon.rss.feed

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode

/**
 * 定期ポーリングはリクエストの外で動くので、1 回の確認と 1 本の取り込みは span に載せないと後から追えない。
 */
internal object FeedPollSpan {
    val FEED_ID: AttributeKey<Long> = AttributeKey.longKey("feed.poll.feed_id")
    val HOST: AttributeKey<String> = AttributeKey.stringKey("feed.poll.host")
    val DUE_COUNT: AttributeKey<Long> = AttributeKey.longKey("feed.poller.due_count")
    val ERROR: AttributeKey<String> = AttributeKey.stringKey("feed.poll.error")

    fun set(
        key: AttributeKey<String>,
        value: String?,
    ) {
        if (value != null) Span.current().setAttribute(key, value)
    }

    fun set(
        key: AttributeKey<Long>,
        value: Long,
    ) {
        Span.current().setAttribute(key, value)
    }

    fun pollFailed(error: String) {
        set(ERROR, error)
        Span.current().setStatus(StatusCode.ERROR)
    }

    fun pollFailed(failure: Throwable) {
        val span = Span.current()
        span.recordException(failure)
        span.setStatus(StatusCode.ERROR)
    }
}
