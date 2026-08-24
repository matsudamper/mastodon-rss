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
import net.matsudamper.mastodon.rss.shared.AccountId

object AccountIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("AccountId")
        .description("DB 上のアカウント id")
        .coercing(AccountIdCoercing)
        .build()

    private object AccountIdCoercing : Coercing<AccountId, Long> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
            return (dataFetcherResult as? AccountId)?.value
                ?: throw CoercingSerializeException("AccountId にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): AccountId {
            return when (input) {
                is AccountId -> input
                is Number -> AccountId(input.toLong())
                is String -> input.toLongOrNull()?.let(::AccountId)
                else -> null
            } ?: throw CoercingParseValueException("AccountId として読めない: $input")
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): AccountId {
            val raw = when (input) {
                // toLong() は桁が溢れると別の値に化ける
                is IntValue -> runCatching { input.value.longValueExact() }.getOrNull()

                is StringValue -> input.value?.toLongOrNull()

                else -> null
            }
            return raw?.let(::AccountId)
                ?: throw CoercingParseLiteralException("AccountId は整数で書く")
        }
    }
}
