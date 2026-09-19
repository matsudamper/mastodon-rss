package net.matsudamper.mastodon.rss.graphql.resolver

import net.matsudamper.mastodon.rss.graphql.model.QlAdminDeliveryKind
import net.matsudamper.mastodon.rss.repository.DeliveryKind

/**
 * 滞っている配信が何なのかは宛先だけでは分からないので、種別をそのまま出す
 */
internal fun DeliveryKind.toGraphqlResponse(): QlAdminDeliveryKind =
    when (this) {
        DeliveryKind.CREATE_NOTE -> QlAdminDeliveryKind.CREATE_NOTE
        DeliveryKind.DELETE_NOTE -> QlAdminDeliveryKind.DELETE_NOTE
        DeliveryKind.ACCEPT_FOLLOW -> QlAdminDeliveryKind.ACCEPT_FOLLOW
        DeliveryKind.UPDATE_ACTOR -> QlAdminDeliveryKind.UPDATE_ACTOR
        DeliveryKind.DELETE_ACTOR -> QlAdminDeliveryKind.DELETE_ACTOR
    }
