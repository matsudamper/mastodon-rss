package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// graphql-java が扱う素の値と kotlinx.serialization の [JsonElement] の変換。
//
// ContentNegotiation を使わない（理由は `json/JsonResponse.kt`）ので、
// 本文の読み書きは自分でやることになる。graphql-java は `Map` と `List` と
// 素のスカラーしか知らないため、境界のここで変換する。

/**
 * 実行結果を JSON にする。
 *
 * 知らない型が来たら落とす。黙って `toString()` すると、レスポンスの形が
 * 型によって変わるうえ、変換に失敗していることに気付けない。
 */
fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> {
            JsonNull
        }

        is String -> {
            JsonPrimitive(this)
        }

        is Boolean -> {
            JsonPrimitive(this)
        }

        is Number -> {
            JsonPrimitive(this)
        }

        is Map<*, *> -> {
            JsonObject(
                entries.associate { (key, value) ->
                    // GraphQL のフィールド名は文字列でしか来ない。他のものが来たら組み立てが壊れている
                    require(key is String) { "GraphQL の結果のキーが文字列ではない: $key" }
                    key to value.toJsonElement()
                },
            )
        }

        is List<*> -> {
            JsonArray(map { it.toJsonElement() })
        }

        else -> {
            throw IllegalArgumentException("GraphQL の結果に知らない型が入っている: ${this::class.qualifiedName}")
        }
    }

/**
 * 変数を graphql-java に渡せる形にする。
 *
 * 変数は型が決まらないので [JsonObject] のまま受け、実行の直前にここで開く。
 * `@Serializable` な型に落とすと、スキーマを増やすたびに受け皿の型が要る。
 */
fun JsonElement.toRawValue(): Any? =
    when (this) {
        is JsonNull -> {
            null
        }

        is JsonPrimitive -> {
            when {
                // 引用符が付いていたものは、中身が数字に見えても文字列のまま渡す
                isString -> content

                else -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }

        is JsonObject -> {
            mapValues { (_, value) -> value.toRawValue() }
        }

        is JsonArray -> {
            map { it.toRawValue() }
        }
    }
