package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.shared.AccountId

internal fun ActorUrls.toGraphqlResponse(accountId: AccountId): QlAccount = QlAccount(
    id = accountId,
    username = username,
    acct = mention,
    actorUrl = actorId,
)
