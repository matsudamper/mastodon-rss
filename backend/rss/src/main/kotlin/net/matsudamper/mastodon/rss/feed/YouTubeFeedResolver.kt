package net.matsudamper.mastodon.rss.feed

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 貼られた URL から YouTube のフィード URL を作る。
 *
 * YouTube はチャンネルと再生リストの Atom 1.0 を今も配信していて、中身は [FeedParser] で
 * そのまま読める。ただし引ける URL は
 * `https://www.youtube.com/feeds/videos.xml?channel_id=UC...` の形だけで、
 * 人が普段目にする URL（`/@handle` や動画のページ）とは別物になっている。
 * 登録のたびに利用者へチャンネル ID を調べさせるわけにはいかないので、ここで変換する。
 *
 * ネットワークは触らない。このモジュールは HTTP クライアントを持たないし、
 * URL の形を決めるだけなら通信は要らない。`/@handle` のようにチャンネル ID が
 * ページの中にしか無い形は [YouTubeFeedSource.NeedsPageLookup] として返し、
 * 取得は HTTP クライアントを持つ `:backend` 側に任せる。取ってきた HTML から
 * ID を抜き出すのは [channelIdFromPageHtml] で、これも文字列を見るだけ。
 *
 * `/user/<名前>` を `?user=<名前>` のフィードにそのまま読み替えることはしない。
 * これは旧ユーザー名の時代の入口で、いま同じ綴りのチャンネルがあるとは限らない。
 * 実際 `?user=MrBeast` は 404 ではなく別人のフィードを 200 で返す。
 * 間違ったチャンネルを黙って購読するくらいなら、ページを引いて確かめる方がよい。
 */
object YouTubeFeedResolver {
    private const val FEED_ENDPOINT = "https://www.youtube.com/feeds/videos.xml"
    private const val WATCH_ENDPOINT = "https://www.youtube.com/watch"
    private const val SITE = "https://www.youtube.com"

    /** チャンネル ID。`UC` + 22 文字で固定 */
    private val channelIdPattern = Regex("UC[A-Za-z0-9_-]{22}")

    /** 動画 ID。11 文字で固定 */
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    /** 再生リスト ID。接頭辞ごとに長さが違うので幅を持たせる */
    private val playlistIdPattern = Regex("[A-Za-z0-9_-]{2,64}")

    /** ハンドル。`@` の後ろに英数字と `.` `_` `-` */
    private val handlePattern = Regex("@[A-Za-z0-9._-]{1,60}")

    /** 旧ユーザー名とカスタム URL の名前 */
    private val namePattern = Regex("[A-Za-z0-9._-]{1,100}")

    /** スキームが付いているか。付いていなければ https として読む */
    private val schemePattern = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    /** ページが自分で名乗っているフィードの URL */
    private val feedLinkInPage = Regex("""feeds/videos\.xml\?channel_id=(UC[A-Za-z0-9_-]{22})""")

    /** `rel="canonical"` と `og:url`。JSON の中では `/` が `\/` になっていることがある */
    private val channelPathInPage = Regex("""youtube\.com\\?/channel\\?/(UC[A-Za-z0-9_-]{22})""")

    /** 埋め込まれた JSON。動画のページはこれで拾う */
    private val channelIdInJson = Regex(""""(?:externalId|channelId)"\s*:\s*"(UC[A-Za-z0-9_-]{22})"""")

    /**
     * ホスト。`www.` `m.` `music.` は落としてから突き合わせる。
     * `youtube-nocookie.com` は埋め込みプレイヤーの URL に出てくる
     */
    private val hosts = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /** `/shorts/<id>` のように、2 つめの区切りに動画 ID が入るパス */
    private val videoPathPrefixes = setOf("shorts", "live", "embed", "v")

    /**
     * URL を読んでフィードの引き方を決める。
     *
     * YouTube の URL でない場合と、YouTube だがフィードに繋げられない形
     * （検索結果やアカウント設定のページなど）は null を返す。
     * 「対応していない」と「YouTube ですらない」を呼び出し側で出し分ける必要が出たら、
     * そのときに戻り値を分ければよい。いまはどちらも登録できないという意味で同じ。
     */
    fun resolve(input: String): YouTubeFeedSource? {
        val uri = parseUri(input) ?: return null
        if (uri.scheme != null && uri.scheme.lowercase() !in setOf("http", "https")) return null

        val host = normalizeHost(uri.host) ?: return null
        if (host !in hosts) return null

        val segments =
            uri.path
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        val query = parseQuery(uri.rawQuery)

        // youtu.be は短縮 URL なので、パスの先頭がそのまま動画 ID になる
        if (host == "youtu.be") {
            return segments.firstOrNull()?.let { videoLookup(it) }
        }

        val first = segments.firstOrNull() ?: return null
        val second = segments.getOrNull(1)

        return when {
            // 既にフィードの URL。そのまま通す
            first == "feeds" && second == "videos.xml" -> {
                fromFeedQuery(query)
            }

            first == "channel" -> {
                second?.let { channelFeed(it) }
            }

            first.startsWith("@") -> {
                if (!handlePattern.matches(first)) return null
                YouTubeFeedSource.NeedsPageLookup("$SITE/$first")
            }

            // カスタム URL と旧ユーザー名。どちらも綴りからは ID が分からない
            first == "c" || first == "user" -> {
                if (second == null || !namePattern.matches(second)) return null
                YouTubeFeedSource.NeedsPageLookup("$SITE/$first/$second")
            }

            first == "playlist" -> {
                query["list"]?.let { playlistFeed(it) }
            }

            // 再生リストの中の動画。`v` があれば動画として扱う。
            // 貼った人が見ていたのはその動画で、`list` は再生の文脈に過ぎないため
            first == "watch" -> {
                query["v"]?.let { videoLookup(it) } ?: query["list"]?.let { playlistFeed(it) }
            }

            first in videoPathPrefixes -> {
                second?.let { videoLookup(it) }
            }

            else -> {
                null
            }
        }
    }

    /** チャンネル ID からフィードの URL を作る。ID の形が違えば null */
    fun feedUrlForChannel(channelId: String): String? {
        if (!channelIdPattern.matches(channelId)) return null
        return "$FEED_ENDPOINT?channel_id=$channelId"
    }

    /** 再生リスト ID からフィードの URL を作る。ID の形が違えば null */
    fun feedUrlForPlaylist(playlistId: String): String? {
        if (!isSubscribablePlaylist(playlistId)) return null
        return "$FEED_ENDPOINT?playlist_id=$playlistId"
    }

    /**
     * チャンネルや動画のページの HTML からチャンネル ID を抜き出す。
     *
     * [YouTubeFeedSource.NeedsPageLookup] を受け取った側が、ページを取ってから呼ぶ。
     * HTML を組み立て直さずに正規表現で拾うのは、相手が YouTube の描画する巨大な
     * ページで、構造が変わっても ID の書き方は変わりにくいため。
     *
     * 探す順は次のとおりで、どれも実際のページで確認している。
     *
     * 1. ページが自分で名乗っているフィードの URL（`rel="alternate"` の RSS リンク）
     * 2. `rel="canonical"` と `og:url` の `/channel/<id>`
     * 3. 埋め込まれた JSON の `externalId` と `channelId`
     *
     * 3 は動画のページ用。チャンネルのページなら 1 で当たる。
     * 見つからなければ null を返す。ページの取得に失敗した（同意画面やレート制限に
     * 飛ばされた）場合もここに落ちるので、呼び出し側は取得の成否と分けて扱わないこと。
     */
    fun channelIdFromPageHtml(html: String): String? =
        feedLinkInPage.find(html)?.groupValues?.get(1)
            ?: channelPathInPage.find(html)?.groupValues?.get(1)
            ?: channelIdInJson.find(html)?.groupValues?.get(1)

    private fun fromFeedQuery(query: Map<String, String>): YouTubeFeedSource? {
        query["channel_id"]?.let { return channelFeed(it) }
        query["playlist_id"]?.let { return playlistFeed(it) }
        // 旧ユーザー名のフィードは当てにならないので、ページから引き直す
        query["user"]?.let {
            if (!namePattern.matches(it)) return null
            return YouTubeFeedSource.NeedsPageLookup("$SITE/user/$it")
        }
        return null
    }

    private fun channelFeed(channelId: String): YouTubeFeedSource? {
        val url = feedUrlForChannel(channelId) ?: return null
        return YouTubeFeedSource.Feed(
            url = url,
            kind = YouTubeFeedSource.Kind.CHANNEL,
            id = channelId,
        )
    }

    private fun playlistFeed(playlistId: String): YouTubeFeedSource? {
        val url = feedUrlForPlaylist(playlistId) ?: return null
        return YouTubeFeedSource.Feed(
            url = url,
            kind = YouTubeFeedSource.Kind.PLAYLIST,
            id = playlistId,
        )
    }

    private fun videoLookup(videoId: String): YouTubeFeedSource? {
        if (!videoIdPattern.matches(videoId)) return null
        return YouTubeFeedSource.NeedsPageLookup("$WATCH_ENDPOINT?v=$videoId")
    }

    /**
     * 購読できる再生リストか。
     *
     * `RD` で始まるのは YouTube がその場で作るミックスで、フィードは 404 になる。
     * `WL`（後で見る）と `LL`（高く評価した動画）は本人にしか見えない。
     * どれも登録した時点では気付けず、後から取得が失敗し続けるだけなので、入口で落とす。
     */
    private fun isSubscribablePlaylist(playlistId: String): Boolean {
        if (!playlistIdPattern.matches(playlistId)) return false
        if (playlistId.startsWith("RD")) return false
        return playlistId != "WL" && playlistId != "LL"
    }

    private fun parseUri(input: String): URI? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // スキームの無い `youtube.com/@name` や、`//youtube.com/...` も受け取る。
        // 貼られる URL は人が選択してコピーしたもので、頭が欠けていることがある
        val normalized =
            when {
                schemePattern.containsMatchIn(trimmed) -> trimmed
                trimmed.startsWith("//") -> "https:$trimmed"
                else -> "https://$trimmed"
            }
        return try {
            URI(normalized)
        } catch (_: URISyntaxException) {
            null
        }
    }

    private fun normalizeHost(host: String?): String? {
        val lower = host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        for (prefix in listOf("www.", "m.", "music.")) {
            if (lower.startsWith(prefix)) return lower.removePrefix(prefix)
        }
        return lower
    }

    /**
     * クエリを読む。同じ名前が複数あれば最初のものを採る。
     *
     * `URI` はクエリを分解してくれないので自分で割る。値は percent-encoding と
     * `+` の両方が来るので [URLDecoder] に通す。
     */
    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val name = decode(part.substring(0, separator)) ?: continue
            val value = decode(part.substring(separator + 1)) ?: continue
            if (value.isEmpty()) continue
            result.putIfAbsent(name, value)
        }
        return result
    }

    private fun decode(value: String): String? =
        try {
            URLDecoder.decode(value, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            // 壊れた percent-encoding。その項目だけ捨てる
            null
        }
}

/**
 * 貼られた URL から分かったこと。
 *
 * フィードの URL がその場で決まる形と、ページを取らないと決まらない形の 2 つに分かれる。
 * この差はこのモジュールでは埋められない（HTTP クライアントを持たない）ので、
 * 型で外に出して呼び出し側に判断させる。
 */
sealed interface YouTubeFeedSource {
    /**
     * フィードの URL が確定した状態。そのまま取得しに行ける。
     *
     * @param url フィードの URL
     * @param kind チャンネルか再生リストか
     * @param id URL に入れた ID。登録するときに控えておくと、後から URL を組み直せる
     */
    data class Feed(
        val url: String,
        val kind: Kind,
        val id: String,
    ) : YouTubeFeedSource

    /**
     * チャンネル ID がページの中にしか無い状態。
     *
     * [pageUrl] を取得して [YouTubeFeedResolver.channelIdFromPageHtml] に渡し、
     * 得られた ID を [YouTubeFeedResolver.feedUrlForChannel] に入れるとフィードの URL になる。
     */
    data class NeedsPageLookup(
        val pageUrl: String,
    ) : YouTubeFeedSource

    enum class Kind {
        CHANNEL,
        PLAYLIST,
    }
}
