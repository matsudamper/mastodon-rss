package net.matsudamper.mastodon.rss.graphql

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import net.matsudamper.mastodon.rss.shared.FeedItemId

object FeedItemIdScalar {
    /**
     * Double が整数を取りこぼさずに表せる幅
     */
    private val WHOLE_RANGE = -(1L shl 53).toDouble()..(1L shl 53).toDouble()

    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("FeedItemId")
        .description("DB 上の記事 id")
        .coercing(FeedItemIdCoercing)
        .build()

    /**
     * 整数をちょうど表している値だけ Long にする
     */
    private fun Number.toWholeLong(): Long? =
        when (this) {
            is Int, is Long, is Short, is Byte -> toLong()
            else -> toDouble().takeIf { it % 1.0 == 0.0 && it in WHOLE_RANGE }?.toLong()
        }

    private object FeedItemIdCoercing : Coercing<FeedItemId, Long> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
            return (dataFetcherResult as? FeedItemId)?.value
                ?: throw CoercingSerializeException("FeedItemId にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): FeedItemId {
            return when (input) {
                is FeedItemId -> input

                // toLong() は小数を切り捨てるので、1.9 が 1 として通ってしまう
                is Number -> input.toWholeLong()?.let(::FeedItemId)

                is String -> input.toLongOrNull()?.let(::FeedItemId)

                else -> null
            } ?: throw CoercingParseValueException("FeedItemId として読めない: $input")
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): FeedItemId {
            val raw = when (input) {
                // toLong() は桁が溢れると別の値に化ける
                is IntValue -> runCatching { input.value.longValueExact() }.getOrNull()

                is StringValue -> input.value?.toLongOrNull()

                else -> null
            }
            return raw?.let(::FeedItemId)
                ?: throw CoercingParseLiteralException("FeedItemId は整数で書く")
        }
    }
}
