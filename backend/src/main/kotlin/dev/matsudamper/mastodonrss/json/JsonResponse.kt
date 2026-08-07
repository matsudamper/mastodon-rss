package dev.matsudamper.mastodonrss.json

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.SerializationStrategy

/**
 * serializer を明示して JSON を返す。
 *
 * Ktor の `ContentNegotiation` を入れて `call.respond(value)` と書くと、
 * 値の [kotlin.reflect.KType] から serializer をリフレクションで引く実装になる。
 * これは native-image では解決できず、`Serializer for class 'Foo' is not found.`
 * として実行時に 500 が返る。JVM のテストは全部通るので native バイナリを
 * 起動するまで気付けない、という形の不具合になる。
 *
 * ここで serializer を引数に取るのは、その解決をコンパイル時に済ませるため。
 * リフレクションが発生しないので `reflect-config.json` への登録も要らない。
 *
 * @param serializer 値のシリアライザ。`Foo.serializer()` で取れる
 * @param contentType 返す Content-Type。ActivityPub のエンドポイントでは
 *   `application/json` ではなく [dev.matsudamper.mastodonrss.activitypub.ActivityPubContentTypes] の値を渡す
 */
suspend fun <T> ApplicationCall.respondJson(
    serializer: SerializationStrategy<T>,
    value: T,
    contentType: ContentType = ContentType.Application.Json,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(
        text = AppJson.encodeToString(serializer, value),
        contentType = contentType,
        status = status,
    )
}
