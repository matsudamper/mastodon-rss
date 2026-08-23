package net.matsudamper.mastodon.rss.graphql // pragma: allowlist secret

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import net.matsudamper.mastodon.rss.shared.AccountId // pragma: allowlist secret

object AccountIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("AccountId")
        .description("DB 上のアカウント id")
        .coercing(AccountIdCoercing)
        .build()

    private object AccountIdCoercing : Coercing<AccountId, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
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
                is String -> AccountId(input)
                else -> throw CoercingParseValueException("AccountId として読めない: $input")
            }
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): AccountId {
            val raw = (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException("AccountId は文字列で書く")
            return AccountId(raw)
        }
    }
}
