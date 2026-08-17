package net.matsudamper.mastodon.rss.staticfiles

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import io.ktor.http.ContentType

/**
 * [root] の下から、リクエストのパスに対応するファイルを引く。
 *
 * 画面は SPA なので、パスの解釈はブラウザ側の仕事になる。サーバーは
 * ファイルがあればそれを返し、無ければ [INDEX_FILE_NAME] を返して frontend に任せる。
 */
class StaticFiles(
    srcDir: Path,
) {
    /** 外に出ていないかの判定に使うので、絶対パスに正規化しておく */
    val root: Path = srcDir.toAbsolutePath().normalize()

    /**
     * 配信できる状態か。ディレクトリごと無ければ何も返せない。
     */
    fun isAvailable(): Boolean = Files.isDirectory(root)

    /**
     * 返すファイルを決める。見つからなければ null。
     *
     * @param segments リクエストパスをスラッシュで区切ったもの。デコード済みのものを渡す
     */
    fun resolve(segments: List<String>): Path? {
        val safeSegments = safeSegments(segments) ?: return null

        val requested = fileOf(safeSegments) ?: return null
        if (Files.isRegularFile(requested)) return requested

        // 拡張子のあるパスは index.html に落とさない。落とすと .js や .wasm の
        // 読み込み失敗が 200 + HTML になり、画面が真っ白になった理由を追えなくなる。
        // ただし `/@name.example` のようなアカウントの画面は除く。ユーザー名には
        // `.` が使えるので、拡張子として扱うと画面が開けなくなる
        val last = safeSegments.lastOrNull()
        if (last?.contains('.') == true && !last.startsWith(ACCOUNT_PREFIX)) return null

        return root.resolve(INDEX_FILE_NAME).takeIf { Files.isRegularFile(it) }
    }

    private fun fileOf(safeSegments: List<String>): Path? {
        if (safeSegments.isEmpty()) return root.resolve(INDEX_FILE_NAME)

        val resolved =
            try {
                root.resolve(safeSegments.joinToString("/")).normalize()
            } catch (_: InvalidPathException) {
                // OS が受け付けない文字（NUL など）が入っている
                return null
            }

        // セグメント単位で弾いた上での二重の確認。シンボリックリンクは辿るが、
        // 置いたのは配信する側なので、ここでは考慮しない
        return resolved.takeIf { it.startsWith(root) }
    }

    /**
     * ディレクトリの外に出るパスを弾く。
     *
     * リクエストのパスをそのまま連結すると `..` で外のファイルを読み出せてしまう。
     * 空のセグメント（`//` や末尾の `/`）は外に出られないので落とすだけにする。
     */
    private fun safeSegments(segments: List<String>): List<String>? {
        val filtered = segments.filter { it.isNotEmpty() }

        val hasUnsafe =
            filtered.any { segment ->
                segment == "." || segment == ".." || segment.contains('/') || segment.contains('\\')
            }

        return filtered.takeIf { !hasUnsafe }
    }

    companion object {
        const val INDEX_FILE_NAME: String = "index.html"

        /**
         * アカウントの画面のパスの目印。
         *
         * 画面側（`:frontend` の `Screen`）が `/@ユーザー名` で開く。
         * ここで見ているのは「拡張子付きに見えても画面のパスである」判定だけで、
         * ユーザー名が実在するかどうかは画面が判断する。
         */
        const val ACCOUNT_PREFIX: String = "@"

        /**
         * 拡張子から Content-Type を決める。
         *
         * Ktor の `ContentType.defaultForFile` を使わず自前で持つのは、あれが
         * jar の中の一覧（mimelist.csv）を読む実装で、native-image ではリソースの
         * 登録が要るため。配信するのは自分で置いたファイルなので、必要な分だけ並べる。
         *
         * `.wasm` を外さないこと。`application/octet-stream` で返すとブラウザが
         * `WebAssembly.instantiateStreaming` に渡せず、画面が真っ白になる。
         */
        fun contentTypeOf(fileName: String): ContentType =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "html" -> ContentType.Text.Html
                "js", "mjs" -> ContentType.Application.JavaScript
                "wasm" -> ContentType.Application.Wasm
                "css" -> ContentType.Text.CSS
                "json", "map" -> ContentType.Application.Json
                "svg" -> ContentType.Image.SVG
                "png" -> ContentType.Image.PNG
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "gif" -> ContentType.Image.GIF
                "ico" -> ContentType.Image.XIcon
                "webp" -> ContentType.Image.WEBP
                "woff2" -> ContentType.Font.Woff2
                "woff" -> ContentType.Font.Woff
                "ttf" -> ContentType.Font.Ttf
                "otf" -> ContentType.Font.Otf
                "txt" -> ContentType.Text.Plain
                "xml" -> ContentType.Application.Xml
                else -> ContentType.Application.OctetStream
            }
    }
}
