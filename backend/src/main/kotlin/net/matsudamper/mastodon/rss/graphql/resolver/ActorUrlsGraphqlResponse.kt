package net.matsudamper.mastodon.rss.graphql.resolver // pragma: allowlist secret

import net.matsudamper.mastodon.rss.actor.ActorUrls // pragma: allowlist secret
import net.matsudamper.mastodon.rss.graphql.model.QlAccount // pragma: allowlist secret
import net.matsudamper.mastodon.rss.repository.AccountId // pragma: allowlist secret
import net.matsudamper.mastodon.rss.shared.AccountId as GraphQlAccountId // pragma: allowlist secret

internal fun ActorUrls.toGraphqlResponse(accountId: AccountId? = null): QlAccount = QlAccount(
    id = accountId?.let { GraphQlAccountId(it.value.toString()) },
    username = username,
    acct = mention,
    actorUrl = actorId,
)
