package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.graphql.model.QlAccount

internal fun ActorUrls.toGraphqlResponse(): QlAccount = QlAccount(
    username = username,
    acct = mention,
    actorUrl = actorId,
)
