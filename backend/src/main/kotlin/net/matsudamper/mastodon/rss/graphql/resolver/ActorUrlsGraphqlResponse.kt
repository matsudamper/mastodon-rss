package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.actor.ActorUrls
import net.matsudamper.mastodon.rss.graphql.model.QlAccount
import net.matsudamper.mastodon.rss.repository.AccountId
import net.matsudamper.mastodon.rss.shared.AccountId as GraphQlAccountId

internal fun ActorUrls.toGraphqlResponse(accountId: AccountId): QlAccount = QlAccount(
    id = GraphQlAccountId(accountId.value),
    username = username,
    acct = mention,
    actorUrl = actorId,
)
