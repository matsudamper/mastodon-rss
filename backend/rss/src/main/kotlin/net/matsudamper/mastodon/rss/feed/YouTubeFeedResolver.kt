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

    /**
     * ページが自分を名乗る `<link>` と `<meta>`。
     *
     * タグ 1 つを切り出してから中の属性を見る。HTML 全体から URL を探すと、
     * ページに並んでいる別チャンネルへのリンクを先に拾うことがある。
     * JSON の中の URL は `"` が `\"` になっているので、属性としては一致しない。
     */
    private val declaringTag = Regex("""<(?:link|meta)\b[^>]*>""")

    /** ページが自分で名乗っているフィードの `rel="alternate"` */
    private val alternateAttribute = Regex("""\brel="alternate"""")

    /** ページが自分で名乗っている URL の `rel="canonical"` と `og:url` */
    private val canonicalAttribute = Regex("""\b(?:rel="canonical"|property="og:url")""")

    /** 上のタグの中にあるフィードの URL */
    private val feedUrlAttribute =
        Regex("""\b(?:href|content)="[^"]*feeds/videos\.xml\?channel_id=(UC[A-Za-z0-9_-]{22})""")

    /** 上のタグの中にあるチャンネルの URL */
    private val channelUrlAttribute =
        Regex("""\b(?:href|content)="[^"]*youtube\.com/channel/(UC[A-Za-z0-9_-]{22})""")

    /** 埋め込まれた JSON。動画のページはこれで拾う */
    private val channelIdInJson = Regex(""""(?:externalId|channelId)"\s*:\s*"(UC[A-Za-z0-9_-]{22})"""")

    /**
     * ホスト。`www.` `m.` `music.` `gaming.` は落としてから突き合わせる。
     * `youtube-nocookie.com` は埋め込みプレイヤーの URL に出てくる
     */
    private val hosts = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /** `/shorts/<id>` のように、2 つめの区切りに動画 ID が入るパス */
    private val videoPathPrefixes = setOf("shorts", "live", "embed", "v")

    /**
     * YouTube 自身のページに使われていて、チャンネルの名前にはならないパス。
     *
     * 先頭が名前だけの `/<名前>` は旧来のカスタム URL だが、`/results` や `/feed` のような
     * 自前のページと綴りの上では区別が付かない。ここに挙げたものは引きに行かない。
     *
     * これは無駄な取得を省くためのもので、正しさはここでは担保していない。
     * 挙げ漏らした自前のページを引いてしまっても、チャンネルのページは
     * [channelIdFromPageHtml] で自分をチャンネルとして名乗ったものだけを通す。
     */
    private val reservedPaths =
        setOf(
            "about", "account", "ads", "attribution_link", "c", "channel", "clip", "creators",
            "embed", "feed", "feeds", "hashtag", "howyoutubeworks", "live", "live_chat",
            "logout", "movies", "new", "oops", "playlist", "playlists", "podcasts", "post",
            "premium", "redirect", "reporthistory", "results", "shorts", "signin", "source",
            "sports", "t", "trending", "upload", "user", "v", "watch", "watch_videos",
        )

    /**
     * URL の中に URL が入っている形を辿る回数の上限。
     *
     * `/attribution_link?u=` は `u` の中身をもう一度読み直すので、`u` に
     * `attribution_link` を入れ子にすると入力の長さの分だけ再帰する。
     * 実在の共有リンクは 1 段しかないので、少ない回数で打ち切る。
     */
    private const val MAX_NESTED_LINK_HOPS = 3

    /**
     * URL を読んでフィードの引き方を決める。
     *
     * YouTube の URL でない場合と、YouTube だがフィードに繋げられない形
     * （検索結果やアカウント設定のページなど）は null を返す。
     * 「対応していない」と「YouTube ですらない」を呼び出し側で出し分ける必要が出たら、
     * そのときに戻り値を分ければよい。いまはどちらも登録できないという意味で同じ。
     */
    fun resolve(input: String): YouTubeFeedSource? = resolve(input, MAX_NESTED_LINK_HOPS)

    private fun resolve(input: String, remainingHops: Int): YouTubeFeedSource? {
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
                channelPageLookup("$SITE/$first")
            }

            // カスタム URL と旧ユーザー名。どちらも綴りからは ID が分からない
            first == "c" || first == "user" -> {
                if (second == null || !namePattern.matches(second)) return null
                channelPageLookup("$SITE/$first/$second")
            }

            first == "playlist" -> {
                query["list"]?.let { playlistFeed(it) }
            }

            // 再生リストの中の動画。`v` があれば動画として扱う。
            // 貼った人が見ていたのはその動画で、`list` は再生の文脈に過ぎないため
            first == "watch" -> {
                query["v"]?.let { videoLookup(it) } ?: query["list"]?.let { playlistFeed(it) }
            }

            // 埋め込みプレイヤーの再生リストと配信。どちらも 2 つめがちょうど 11 文字で、
            // videoIdPattern に通ってしまう。動画として読む前に外す
            first == "embed" && second == "videoseries" -> {
                query["list"]?.let { playlistFeed(it) }
            }

            first == "embed" && second == "live_stream" -> {
                query["channel"]?.let { channelFeed(it) }
            }

            first in videoPathPrefixes -> {
                second?.let { videoLookup(it) }
            }

            // 旧い共有リンク。`u` に `/watch?v=...` が percent-encoding で入っている
            first == "attribution_link" -> {
                if (remainingHops <= 0) return null
                query["u"]?.takeIf { it.startsWith("/") }?.let { resolve("$SITE$it", remainingHops - 1) }
            }

            // `/c/` を挟まない旧来のカスタム URL。綴りからは ID が分からない
            first.lowercase() !in reservedPaths && namePattern.matches(first) -> {
                channelPageLookup("$SITE/$first")
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
     * 取ってきたページの HTML からチャンネル ID を抜き出す。
     *
     * [YouTubeFeedSource.NeedsPageLookup] を受け取った側が、ページを取ってから
     * [YouTubeFeedSource.NeedsPageLookup.page] と一緒に呼ぶ。HTML を組み立て直さずに
     * 正規表現で拾うのは、相手が YouTube の描画する巨大なページで、構造が変わっても
     * ID の書き方は変わりにくいため。
     *
     * 手掛かりは次の 3 つで、どれも実際のページで確認している。
     *
     * 1. ページが自分で名乗っているフィードの URL（`rel="alternate"` の RSS リンク）
     * 2. `rel="canonical"` と `og:url` の `/channel/<id>`
     * 3. 埋め込まれた JSON の `externalId` と `channelId`
     *
     * 1 と 2 はそのページ自身が名乗ったチャンネルなので、どちらのページでも信じてよい。
     * ただしタグの中を見て確かめること。ページには他のチャンネルへのリンクも並んでいる。
     *
     * 3 を使うのは動画のページだけにする。動画のページは自分をチャンネルとして
     * 名乗らないのでこれしか無いが、一覧のページには並んでいる動画の投稿者の
     * `channelId` も入っていて、チャンネルのつもりで引いたページでこれを拾うと、
     * 貼られたものと関係の無いチャンネルを黙って購読することになる。
     *
     * 見つからなければ null を返す。ページの取得に失敗した（同意画面やレート制限に
     * 飛ばされた）場合もここに落ちるので、呼び出し側は取得の成否と分けて扱わないこと。
     */
    fun channelIdFromPageHtml(page: YouTubeFeedSource.NeedsPageLookup.Page, html: String): String? {
        declaredChannelId(html)?.let { return it }

        return when (page) {
            YouTubeFeedSource.NeedsPageLookup.Page.VIDEO -> channelIdInJson.find(html)?.groupValues?.get(1)
            YouTubeFeedSource.NeedsPageLookup.Page.CHANNEL -> null
        }
    }

    /** ページ自身が `<link>` と `<meta>` で名乗っているチャンネル */
    private fun declaredChannelId(html: String): String? {
        for (tag in declaringTag.findAll(html)) {
            val attributes = tag.value
            if (alternateAttribute.containsMatchIn(attributes)) {
                feedUrlAttribute.find(attributes)?.let { return it.groupValues[1] }
            }
            if (canonicalAttribute.containsMatchIn(attributes)) {
                channelUrlAttribute.find(attributes)?.let { return it.groupValues[1] }
            }
        }
        return null
    }

    private fun channelPageLookup(pageUrl: String): YouTubeFeedSource.NeedsPageLookup =
        YouTubeFeedSource.NeedsPageLookup(
            pageUrl = pageUrl,
            page = YouTubeFeedSource.NeedsPageLookup.Page.CHANNEL,
        )

    private fun fromFeedQuery(query: Map<String, String>): YouTubeFeedSource? {
        query["channel_id"]?.let { return channelFeed(it) }
        query["playlist_id"]?.let { return playlistFeed(it) }
        // 旧ユーザー名のフィードは当てにならないので、ページから引き直す
        query["user"]?.let {
            if (!namePattern.matches(it)) return null
            return channelPageLookup("$SITE/user/$it")
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
        return YouTubeFeedSource.NeedsPageLookup(
            pageUrl = "$WATCH_ENDPOINT?v=$videoId",
            page = YouTubeFeedSource.NeedsPageLookup.Page.VIDEO,
        )
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
        for (prefix in listOf("www.", "m.", "music.", "gaming.")) {
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
     * [pageUrl] を取得して [page] と一緒に [YouTubeFeedResolver.channelIdFromPageHtml] に渡し、
     * 得られた ID を [YouTubeFeedResolver.feedUrlForChannel] に入れるとフィードの URL になる。
     *
     * @param pageUrl 取得するページの URL
     * @param page 何のページとして引くか。ID を信じてよい手掛かりが変わる
     */
    data class NeedsPageLookup(
        val pageUrl: String,
        val page: Page,
    ) : YouTubeFeedSource {
        enum class Page {
            CHANNEL,
            VIDEO,
        }
    }

    enum class Kind {
        CHANNEL,
        PLAYLIST,
    }
}
