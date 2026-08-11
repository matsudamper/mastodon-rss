package net.matsudamper.mastodon.rss.graphql

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// graphql-java が扱う素の値と JsonElement の変換。
// ContentNegotiation を使わない（理由は json/JsonResponse.kt）ので、境界のここで変換する。

/** 実行結果を JSON にする。知らない型で落とすのは、黙って `toString()` すると気付けないため */
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

/** 変数を graphql-java に渡せる形にする */
fun JsonElement.toRawValue(): Any? =
    when (this) {
        is JsonNull -> {
            null
        }

        is JsonPrimitive -> {
            when {
                // 引用符が付いていたものは、数字に見えても文字列のまま渡す
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
