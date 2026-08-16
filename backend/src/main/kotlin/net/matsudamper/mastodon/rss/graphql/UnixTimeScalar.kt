package net.matsudamper.mastodon.rss.graphql

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType

/**
 * スキーマの `UnixTime`。
 *
 * 定義はスキーマ側にあるが、値の変換は graphql-java に登録しないと動かない。
 * 登録漏れはスキーマを組み立てる時点で落ちるので、[GraphQlEngine] から必ず渡す。
 */
object UnixTimeScalar {
    val value: GraphQLScalarType = GraphQLScalarType
        .newScalar()
        .name("UnixTime")
        .description("エポック (1970-01-01T00:00:00Z) からの秒数")
        .coercing(UnixTimeCoercing)
        .build()

    private object UnixTimeCoercing : Coercing<Long, Long> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
            return dataFetcherResult as? Long
                ?: throw CoercingSerializeException("UnixTime にできない型: ${dataFetcherResult::class.qualifiedName}")
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
            return when (input) {
                is Long -> input
                is Int -> input.toLong()
                else -> throw CoercingParseValueException("UnixTime として読めない: $input")
            }
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Long {
            return (input as? IntValue)?.value?.toLong()
                ?: throw CoercingParseLiteralException("UnixTime は整数で書く")
        }
    }
}
