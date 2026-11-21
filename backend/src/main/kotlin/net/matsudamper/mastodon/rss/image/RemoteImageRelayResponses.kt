package net.matsudamper.mastodon.rss.image

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully

/**
 * 中継の枠を使い切っているときの応答。前段に残すと空いた後も出なくなるので持たせない
 */
internal suspend fun ApplicationCall.respondRelayBusy() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.RetryAfter, RETRY_AFTER_SECONDS_WHEN_BUSY.toString())
    respondText("混み合っている", status = HttpStatusCode.ServiceUnavailable)
}

/**
 * 中継する画像を返す。
 *
 * 一度に書くと配列の写しが丸ごと読み手のバッファへ移り、送り終える前に書き手が再開して
 * [RemoteImageRelayPermits] の枠を手放す。区切って書けば、ByteChannel は溜まりが上限に
 * 達すると読み手が受け取るまで書き手を止めるので、枠を手放した後に残る量が小さく収まる
 */
internal suspend fun ApplicationCall.respondRelayedImage(
    bytes: ByteArray,
    contentType: ContentType,
) {
    respondBytesWriter(
        contentType = contentType,
        status = HttpStatusCode.OK,
        contentLength = bytes.size.toLong(),
    ) {
        writeInChunks(bytes)
    }
}

private suspend fun ByteWriteChannel.writeInChunks(bytes: ByteArray) {
    for (start in bytes.indices step RESPONSE_CHUNK_BYTES) {
        writeFully(bytes, startIndex = start, endIndex = minOf(start + RESPONSE_CHUNK_BYTES, bytes.size))
        flush()
    }
}

private const val RETRY_AFTER_SECONDS_WHEN_BUSY = 5
private const val RESPONSE_CHUNK_BYTES = 64 * 1024
