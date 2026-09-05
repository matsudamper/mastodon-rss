package net.matsudamper.mastodon.rss.feed

import java.time.Instant

/**
 * フィードの形式。
 *
 * 判定はルート要素で行う。中身の取り出し方が形式ごとに違うので、
 * どれとして読んだのかを結果にも残しておく（取り込みが空になったときに、
 * 形式の判定を間違えたのか中身が無いのかを切り分けられるようにするため）。
 */
enum class FeedFormat {
    /** `<rss version="2.0">`。0.91 / 0.92 も要素名は同じなのでこれとして読む */
    RSS_2_0,

    /** `<rdf:RDF>`。RSS 1.0。日本語圏の配信元でまだ使われている */
    RSS_1_0,

    /** `<feed xmlns="http://www.w3.org/2005/Atom">` */
    ATOM_1_0,
}

/**
 * 本文の中身と、それが HTML なのかプレーンテキストなのか。
 */
data class FeedContent(
    val text: String,
    val type: Type,
) {
    enum class Type {
        TEXT,
        HTML,
    }

    /** 表示用のプレーンテキスト。HTML ならタグを落とし、実体参照を戻す */
    fun toPlainText(): String =
        when (type) {
            Type.TEXT -> FeedText.normalizeWhitespace(text)
            Type.HTML -> HtmlSanitizer.toPlainText(text)
        }

    /**
     * 配信用の HTML。
     *
     * テキストはエスケープしたうえで、改行を `<br>` にする。改行を落とすと
     * 段落の切れ目が消えて 1 つの塊になる。
     */
    fun toSafeHtml(): String =
        when (type) {
            Type.TEXT -> HtmlSanitizer.escapeText(FeedText.normalizeWhitespace(text)).replace("\n", "<br>")
            Type.HTML -> HtmlSanitizer.sanitize(text)
        }
}

/**
 * 解析したフィード 1 本ぶん。
 *
 * DB の行ではなく、XML を読んだ結果をそのまま持つ型。永続化の都合（id や
 * 取得日時）はここには入れない。保存する形は `:backend:repository` 側に
 * 別の型として置き、詰め替えは取り込み処理で行う。
 * こうしておくと、DB のスキーマが変わってもパーサを触らずに済む。
 *
 * @param format どの形式として読んだか
 * @param title フィードの題名。無い配信元もあるので null を許す
 * @param link フィードに対応する Web ページの URL
 * @param description フィードの説明（Atom の `subtitle`）
 * @param updatedAt フィード全体の更新日時。RSS 2.0 の `lastBuildDate` / Atom の `updated`
 * @param items 記事。XML に現れた順のまま。並べ替えはしない
 */
data class ParsedFeed(
    val format: FeedFormat,
    val title: String?,
    val link: String?,
    val description: FeedContent?,
    val updatedAt: Instant?,
    val items: List<ParsedFeedItem>,
)

/**
 * 記事 1 件ぶん。
 *
 * どのフィールドも欠けうる。必須にすると、1 件壊れているだけでフィード全体が
 * 取り込めなくなる。使えるものだけで投稿を組み立てられるようにしておく。
 *
 * @param id `guid` / Atom の `id`。差分検出の第一候補。詳細は [FeedItemKey]
 * @param title 記事の題名。タグと実体参照を落としたプレーンテキストにしてある
 * @param link 記事の URL。Atom は `rel="alternate"` のリンクを採る
 * @param summary 要約。RSS の `description` / Atom の `summary`
 * @param content 本文。RSS の `content:encoded` / Atom の `content`。
 *   要約しか無い配信元も、本文しか無い配信元もあるので両方持つ
 * @param publishedAt 公開日時。`pubDate` / `dc:date` / Atom の `published`
 * @param updatedAt 更新日時。Atom の `updated`
 */
data class ParsedFeedItem(
    val id: String?,
    val title: String?,
    val link: String?,
    val summary: FeedContent?,
    val content: FeedContent?,
    val publishedAt: Instant?,
    val updatedAt: Instant?,
) {
    /**
     * 投稿の本文にするなら、という優先順で中身を 1 つ選ぶ。
     *
     * 本文があればそちら、無ければ要約。どちらも無ければ null。
     */
    fun bodyOrSummary(): FeedContent? = content ?: summary
}
