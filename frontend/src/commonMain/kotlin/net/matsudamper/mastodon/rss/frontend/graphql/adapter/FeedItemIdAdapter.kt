package net.matsudamper.mastodon.rss.frontend.graphql.adapter

import com.apollographql.apollo.api.Adapter
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.json.JsonReader
import com.apollographql.apollo.api.json.JsonWriter
import net.matsudamper.mastodon.rss.shared.FeedItemId

val FeedItemIdAdapter = object : Adapter<FeedItemId> {
    override fun fromJson(
        reader: JsonReader,
        customScalarAdapters: CustomScalarAdapters,
    ): FeedItemId = FeedItemId(reader.nextLong())

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: FeedItemId,
    ) {
        writer.value(value.value)
    }
}
