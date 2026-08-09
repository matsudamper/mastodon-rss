package net.matsudamper.mastodon.rss.staticfiles

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * 静的ファイルの配信。管理画面はここから始まる。
 *
 * 配信するのは root から。管理画面は SPA で、画面のパスは全部 1 つの
 * `index.html` から始まるため、`/admin` の下だけを配信する形にはしない。
 *
 * サーバー自身が持つパス（`/healthz` や ActivityPub のもの）はここには来ない。
 * Ktor は固定のパスをテールカードより優先して選ぶため、残りだけが落ちてくる。
 * 読む側にも同じ順に見えるよう、routing の最後で呼ぶこと。
 *
 * @param staticFiles 配信元。未設定またはディレクトリが無いときは null。
 *   この場合は 404 を返す。理由は起動ログに出している
 */
fun Route.staticRoutes(staticFiles: StaticFiles?) {
    get("/{path...}") {
        if (staticFiles == null) {
            call.respondText(
                "静的ファイルの配信先が無い。STATIC_SRC_DIR を確認すること",
                status = HttpStatusCode.NotFound,
            )
            return@get
        }

        // パスパラメータはデコード済みで、区切りごとに分かれている。
        // 生のパスを自分で切ると %2e%2e のようなエンコードを取りこぼす
        val segments = call.parameters.getAll("path").orEmpty()

        val file = staticFiles.resolve(segments)
        if (file == null) {
            call.respondText("見つからない: /${segments.joinToString("/")}", status = HttpStatusCode.NotFound)
            return@get
        }

        // LocalFileContent は Content-Length と Last-Modified を付け、
        // 中身はストリームで流す。ファイル全体をメモリに載せない
        call.respond(LocalFileContent(file.toFile(), StaticFiles.contentTypeOf(file.fileName.toString())))
    }
}
