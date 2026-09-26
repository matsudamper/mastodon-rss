package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminDomainBlock
import net.matsudamper.mastodon.rss.graphql.model.QlAdminDomainBlockReason
import net.matsudamper.mastodon.rss.repository.DomainBlock
import net.matsudamper.mastodon.rss.repository.DomainBlockReason

internal fun DomainBlock.toGraphqlResponse(): QlAdminDomainBlock =
    QlAdminDomainBlock(
        domain = domain,
        reason = when (reason) {
            DomainBlockReason.UNAVAILABLE -> QlAdminDomainBlockReason.UNAVAILABLE
            DomainBlockReason.MANUAL -> QlAdminDomainBlockReason.MANUAL
        },
        reasonDescription = reasonDescription,
        blockDelivery = blockDelivery,
        blockInbox = blockInbox,
        createdAt = createdAt.epochSecond,
    )
