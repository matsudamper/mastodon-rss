package net.matsudamper.mastodon.rss.admin

import io.ktor.http.ContentType

/**
 * 管理画面の静的ファイル（`:frontend` の Kotlin/Wasm ビルド成果物）をリソースから読む。
 *
 * Ktor の `staticResources` を使わず自前で読んでいる理由は 2 つある。
 *
 * 1 つは Content-Type。`.wasm` は `application/wasm` で返さないとブラウザが
 * `WebAssembly.instantiateStreaming` に渡せず、画面が真っ白になる。拡張子の対応表を
 * 自分で持てば取りこぼしが無い。
 *
 * もう 1 つは native-image。`staticResources` はリソースの URL を見てファイル系か
 * jar 系かを判断するが、native バイナリでのリソースの見え方は JVM と異なる。
 * `getResourceAsStream` はどちらでも同じように使えるので、`:backend:repository` が
 * マイグレーション SQL を読むときと同じやり方に揃える。
 *
 * リソースを native バイナリに含めるには `resource-config.json` への登録が要る。
 * 登録は `backend/src/main/resources/META-INF/native-image/` にある。
 *
 * @param basePackage リソース上の置き場所。テストから差し替える
 */
internal class AdminStaticContent(
    private val basePackage: String = DEFAULT_BASE_PACKAGE,
) {
    /**
     * `/admin/` から先のパスに対応するファイルを読む。無ければ null。
     *
     * リクエストのパスをそのままリソース名に使うので、`.` や `..` を含むものは弾く。
     * 通せば jar やバイナリの中の任意のリソースを読み出せてしまう。
     * 受け取るのは Ktor がデコードしたあとの区切り済みの形にしている。
     * 生のパスを自分で分解すると `%2e%2e` の類を見落とす。
     */
    fun read(segments: List<String>): AdminStaticFile? {
        val cleaned = segments.filter { it.isNotEmpty() }

        if (cleaned.isEmpty()) return readIndex()
        if (cleaned.any { it == "." || it == ".." || it.contains('/') || it.contains('\\') }) return null

        val resourcePath = (listOf(basePackage) + cleaned).joinToString("/")
        val bytes =
            AdminStaticContent::class.java.classLoader
                .getResourceAsStream(resourcePath)
                ?.use { it.readBytes() }
                ?: return null

        return AdminStaticFile(
            bytes = bytes,
            contentType = contentTypeOf(cleaned.last()),
        )
    }

    /** 画面の入口。パスに対応するファイルが無いときはこれを返して frontend 側に解釈させる */
    fun readIndex(): AdminStaticFile? = read(listOf(INDEX_FILE))

    companion object {
        /** `:backend` の resources 内の置き場所。`:frontend` の成果物がここに入る */
        const val DEFAULT_BASE_PACKAGE: String = "static"

        const val INDEX_FILE: String = "index.html"

        /**
         * 拡張子ごとの Content-Type。
         *
         * `.wasm` は Ktor の既定の対応表に無く、`application/octet-stream` で返すと
         * ブラウザが実行を拒否する。`.js` も `text/javascript` でないと
         * module script として読まれない。
         */
        private val CONTENT_TYPES: Map<String, ContentType> =
            mapOf(
                "html" to ContentType.Text.Html,
                "js" to ContentType.Text.JavaScript,
                "mjs" to ContentType.Text.JavaScript,
                "css" to ContentType.Text.CSS,
                "wasm" to ContentType("application", "wasm"),
                "json" to ContentType.Application.Json,
                "map" to ContentType.Application.Json,
                "svg" to ContentType.Image.SVG,
                "png" to ContentType.Image.PNG,
                "ico" to ContentType("image", "x-icon"),
                "txt" to ContentType.Text.Plain,
                "woff" to ContentType("font", "woff"),
                "woff2" to ContentType("font", "woff2"),
                "ttf" to ContentType("font", "ttf"),
            )

        internal fun contentTypeOf(fileName: String): ContentType {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return CONTENT_TYPES[extension] ?: ContentType.Application.OctetStream
        }
    }
}

/**
 * 読み込んだ静的ファイル。
 *
 * 全部メモリに読んでから返す。管理画面を開くのは運用者だけで同時アクセスが無く、
 * ストリームのまま返すより取り回しが単純なため。
 */
internal class AdminStaticFile(
    val bytes: ByteArray,
    val contentType: ContentType,
)
