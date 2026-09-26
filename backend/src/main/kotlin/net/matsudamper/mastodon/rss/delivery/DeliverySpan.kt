package net.matsudamper.mastodon.rss.delivery

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode

/**
 * 配信はリクエストの外で動くので、1 行ごとの扱いは span に載せないと後から追えない。
 */
internal object DeliverySpan {
    val ID: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.id")
    val KIND: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.kind")
    val SENDER: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.sender")
    val INBOX: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.inbox")
    val ATTEMPTS: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.attempts")
    val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.outcome")
    val FAILURE_REASON: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.failure_reason")

    fun set(
        key: AttributeKey<String>,
        value: String?,
    ) {
        if (value != null) Span.current().setAttribute(key, value)
    }

    fun outcome(value: String) {
        set(OUTCOME, value)
    }

    fun failed(reason: String) {
        set(FAILURE_REASON, reason)
        Span.current().setStatus(StatusCode.ERROR)
    }

    fun failed(failure: Throwable) {
        val span = Span.current()
        span.recordException(failure)
        span.setStatus(StatusCode.ERROR)
    }
}
