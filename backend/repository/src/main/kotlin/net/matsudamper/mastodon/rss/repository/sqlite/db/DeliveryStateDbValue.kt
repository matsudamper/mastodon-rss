package net.matsudamper.mastodon.rss.repository.sqlite.db

internal enum class DeliveryStateDbValue(
    internal val dbValue: String,
) {
    PENDING("pending"),
    DELIVERING("delivering"),
    FAILED("failed"),
}
