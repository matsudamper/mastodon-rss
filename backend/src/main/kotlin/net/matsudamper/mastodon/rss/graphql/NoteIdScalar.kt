package net.matsudamper.mastodon.rss.graphql

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

object NoteIdScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("NoteId")
        .description("投稿の公開 id")
        .coercing(NoteIdCoercing)
        .build()

    private object NoteIdCoercing : Coercing<String, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return dataFetcherResult as? String
                ?: throw CoercingSerializeException("NoteId にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return input as? String
                ?: throw CoercingParseValueException("NoteId として読めない: $input")
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            return (input as? StringValue)?.value
                ?: throw CoercingParseLiteralException("NoteId は文字列で書く")
        }
    }
}
