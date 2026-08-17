package net.matsudamper.mastodon.rss.staticfiles

import kotlin.io.path.extension
import kotlin.io.path.name
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * アカウントの画面のパスの目印。
 *
 * 画面側（`:frontend` の `Screen`）が `/@ユーザー名` で開く。ユーザー名に `@` は
 * 使えないので、これで一意に判別できる。
 */
private const val ACCOUNT_PREFIX: String = "@"

private const val NO_STATIC_FILES_MESSAGE: String = "静的ファイルの配信先が無い。STATIC_SRC_DIR を確認すること"

/**
 * 入口は画面のパスでも同じ扱いにする。返すファイルが同じなので、扱いが分かれると片方だけ古くなる
 */
private const val INDEX_CACHE_CONTROL: String = "no-store"

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
    // 名前に中身のハッシュが入る拡張子。フォントや画像は名前が変わらないので入れない
    val hashedNameExtensions = setOf("js", "wasm")

    // アカウントの画面は受ける段階で分ける。ユーザー名には `.` が使えるので、
    // ファイルを引く経路に流すと `/@name.example` が拡張子付きのファイル要求に見え、
    // 画面が開けなくなる。ファイルを引く側は拡張子だけを見ればよくなる。
    //
    // 末尾のスラッシュは Ktor では別のパスになる。画面側は空のセグメントを無視して
    // 同じ画面を出すので、両方受けないと `/@name/` だけ開けない
    listOf("", "/").forEach { trailing ->
        get("/$ACCOUNT_PREFIX{username}$trailing") {
            if (staticFiles == null) {
                call.respondText(NO_STATIC_FILES_MESSAGE, status = HttpStatusCode.NotFound)
                return@get
            }

            val index = staticFiles.index()
            if (index == null) {
                call.respondText("見つからない: ${call.request.path()}", status = HttpStatusCode.NotFound)
                return@get
            }

            call.response.header(HttpHeaders.CacheControl, INDEX_CACHE_CONTROL)

            // 名前が実在するかどうかはここでは見ない。画面が GraphQL で確かめる。
            // ここでも判断すると、同じ判定がサーバーと画面の 2 か所に増える
            call.respond(LocalFileContent(index.toFile(), StaticFiles.contentTypeOf(index.fileName.toString())))
        }
    }

    get("/{path...}") {
        if (staticFiles == null) {
            call.respondText(NO_STATIC_FILES_MESSAGE, status = HttpStatusCode.NotFound)
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

        when {
            // 入口だけは毎回取りに行かせる。中から読むファイルの名前は中身が変わると変わるので、
            // ここが古いままだと、既に無い名前を取りに行って画面が出ない
            file.fileName.name == StaticFiles.INDEX_FILE_NAME -> {
                call.response.header(HttpHeaders.CacheControl, INDEX_CACHE_CONTROL)
            }

            // 名前にハッシュが入っているので、中身が変われば別の URL になる
            file.fileName.extension in hashedNameExtensions -> {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            }
        }

        // LocalFileContent は Content-Length と Last-Modified を付け、
        // 中身はストリームで流す。ファイル全体をメモリに載せない
        call.respond(LocalFileContent(file.toFile(), StaticFiles.contentTypeOf(file.fileName.toString())))
    }
}
