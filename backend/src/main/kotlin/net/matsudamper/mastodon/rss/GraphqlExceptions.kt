package net.matsudamper.mastodon.rss

sealed class GraphqlExceptions(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Admin : GraphqlExceptions("ログインしていない")
}
