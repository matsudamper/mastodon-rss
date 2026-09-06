package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/**
 * RSS 2.0 / RSS 1.0 (RDF) / Atom 1.0 を読む。
 *
 * ライブラリは足さず StAX (`javax.xml.stream`) で自前で読む。方針は次のとおり。
 *
 * - 形式の判定はルート要素だけで行う。`<rss>` / `<rdf:RDF>` / `<feed>` の 3 つ
 * - 要素は名前空間の接頭辞ではなくローカル名で見る。接頭辞は配信元が自由に決められる
 * - 知らない要素は中身ごと読み飛ばす。拡張要素（`media:*` など）は珍しくない
 * - 欠けているフィールドは null にする。1 つ足りないだけで全体を失敗にしない
 * - 壊れた XML は [FeedParseException] にする。ここは握り潰さない。
 *   取得は成功したのに中身が読めていない状態を、呼び出し側が黙って続けられると
 *   「新着が無い」と区別が付かなくなる
 *
 * 外部エンティティと DTD は止めてある。フィードの URL は利用者が登録するもので、
 * 中身は相手のサーバーが返すものなので、XXE と展開攻撃（billion laughs）の
 * 入口になりうる。副作用として、DTD で定義された実体参照（`&nbsp;` を宣言だけして
 * 使っているもの）を含む壊れたフィードは読めないが、そこまで面倒を見ない。
 */
object FeedParser {
    /**
     * バイト列から読む。文字コードは XML 宣言と BOM から StAX が判定する。
     *
     * 文字列ではなくバイト列を入口にしているのは、日本語圏の配信元に
     * Shift_JIS や EUC-JP がまだあるため。HTTP のレスポンスを先に String に
     * してしまうと、そこで文字コードを間違えて記録が壊れる。
     */
    fun parse(
        bytes: ByteArray,
        limits: FeedParserLimits = FeedParserLimits(),
    ): ParsedFeed = parse(ByteArrayInputStream(bytes), limits)

    /**
     * ストリームから読む。読み終わっても閉じない（開いた側が閉じる）。
     *
     * 入力の大きさはここでは見ない。上限は取得する側で掛けること。
     */
    fun parse(
        input: InputStream,
        limits: FeedParserLimits = FeedParserLimits(),
    ): ParsedFeed = parse(limits) { createXMLStreamReader(input) }

    /**
     * 文字列から読む。文字コードが分かっている場合とテスト用。
     *
     * XML 宣言に書かれた文字コードは、この入口では既に手遅れなので見ない。
     */
    fun parse(
        xml: String,
        limits: FeedParserLimits = FeedParserLimits(),
    ): ParsedFeed = parse(limits) { createXMLStreamReader(StringReader(xml)) }

    private fun parse(
        limits: FeedParserLimits,
        createReader: XMLInputFactory.() -> XMLStreamReader,
    ): ParsedFeed {
        val reader =
            try {
                createInputFactory().createReader()
            } catch (e: XMLStreamException) {
                throw FeedParseException("XML として読み始められなかった", e)
            }

        try {
            return parseDocument(reader = reader, limits = limits)
        } catch (e: XMLStreamException) {
            throw FeedParseException("XML の解析に失敗した", e)
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun parseDocument(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): ParsedFeed {
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                return when (reader.localName) {
                    "rss" -> parseRss(reader = reader, format = FeedFormat.RSS_2_0, limits = limits)
                    "RDF" -> parseRss(reader = reader, format = FeedFormat.RSS_1_0, limits = limits)
                    "feed" -> parseAtom(reader = reader, limits = limits)
                    else -> throw FeedParseException("知らないルート要素: ${reader.localName}")
                }
            }
        }
        throw FeedParseException("要素が 1 つも無い")
    }

    /**
     * RSS 2.0 と RSS 1.0 を読む。
     *
     * この 2 つは名前空間とルート要素が違うだけで、要素の名前はほぼ同じ。
     * 違うのは `item` の位置で、RSS 2.0 は `channel` の中、RSS 1.0 は
     * `channel` と並んで `RDF` の直下に並ぶ。どちらも拾えるよう、
     * 親が `channel` か `RDF` の `item` を記事として扱う。
     */
    private fun parseRss(
        reader: XMLStreamReader,
        format: FeedFormat,
        limits: FeedParserLimits,
    ): ParsedFeed {
        var title: String? = null
        var link: String? = null
        var description: FeedContent? = null
        var updatedAt: Instant? = null
        val items = mutableListOf<ParsedFeedItem>()

        // 直近の親を見るための積み。`image` や `textInput` の中にも
        // `title` と `link` があるので、`channel` 直下かどうかで判断する
        val path = ArrayDeque<String>()
        path.addLast(reader.localName)

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val name = reader.localName
                    val parent = path.lastOrNull()

                    if (name == "item" && (parent == "channel" || parent == "RDF")) {
                        if (items.size >= limits.maxItems) break
                        items.add(parseRssItem(reader = reader, limits = limits))
                        continue
                    }

                    if (parent != "channel") {
                        path.addLast(name)
                        continue
                    }

                    when (name) {
                        "title" -> {
                            title = FeedText.singleLine(readTextContent(reader, limits))
                        }

                        // RSS 2.0 のフィードには `<atom:link rel="self">` が同居していることがある。
                        // ローカル名で見ているので同じ `link` として届く。中身が空なので
                        // 先に読んだ `<link>` を消さないよう、空でないものだけを採る
                        "link" -> {
                            val linkText = readTextContent(reader, limits).trim()
                            if (link == null && linkText.isNotEmpty()) link = linkText
                        }

                        "description" -> {
                            description =
                                FeedContent(
                                    text = readTextContent(reader, limits),
                                    type = FeedContent.Type.HTML,
                                )
                        }

                        // lastBuildDate が無ければ pubDate、それも無ければ dc:date を見る
                        "lastBuildDate" -> {
                            updatedAt = FeedDates.parse(readTextContent(reader, limits))
                        }

                        "pubDate", "date" -> {
                            val parsedDate = FeedDates.parse(readTextContent(reader, limits))
                            if (updatedAt == null) updatedAt = parsedDate
                        }

                        else -> {
                            path.addLast(name)
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    path.removeLastOrNull()
                }
            }
        }

        return ParsedFeed(
            format = format,
            title = title?.takeIf { it.isNotEmpty() },
            link = link,
            description = description?.takeIf { it.text.isNotBlank() },
            updatedAt = updatedAt,
            items = items,
        )
    }

    private fun parseRssItem(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): ParsedFeedItem {
        var guid: String? = null
        var title: String? = null
        var link: String? = null
        var summary: FeedContent? = null
        var content: FeedContent? = null
        var publishedAt: Instant? = null

        val rdfAboutUri = reader.getAttributeValue(null, "about")?.trim()?.takeIf { it.isNotEmpty() }

        var remainingElementDepth = 1

        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (remainingElementDepth != 1) {
                        remainingElementDepth++
                        continue
                    }
                    when (reader.localName) {
                        "title" -> {
                            title = FeedText.singleLine(readTextContent(reader, limits))
                        }

                        "link" -> {
                            val linkText = readTextContent(reader, limits).trim()
                            if (link == null && linkText.isNotEmpty()) link = linkText
                        }

                        // guid は isPermaLink 属性で URL かどうかを名乗るが、
                        // どちらでも記事を区別する値としては使えるので区別しない
                        "guid" -> {
                            guid = readTextContent(reader, limits).trim().takeIf { it.isNotEmpty() }
                        }

                        "description" -> {
                            summary =
                                FeedContent(
                                    text = readTextContent(reader, limits),
                                    type = FeedContent.Type.HTML,
                                )
                        }

                        // content:encoded。RSS には本文を入れる標準の要素が無いので、
                        // 全文配信の配信元はこの拡張を使う
                        "encoded" -> {
                            content =
                                FeedContent(
                                    text = readTextContent(reader, limits),
                                    type = FeedContent.Type.HTML,
                                )
                        }

                        "pubDate" -> {
                            publishedAt = FeedDates.parse(readTextContent(reader, limits))
                        }

                        // dc:date。RSS 1.0 の日時はこちら
                        "date" -> {
                            val parsedDate = FeedDates.parse(readTextContent(reader, limits))
                            if (publishedAt == null) publishedAt = parsedDate
                        }

                        else -> {
                            remainingElementDepth++
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    remainingElementDepth--
                }
            }
        }

        return ParsedFeedItem(
            id = guid ?: rdfAboutUri,
            title = title?.takeIf { it.isNotEmpty() },
            link = link,
            summary = summary?.takeIf { it.text.isNotBlank() },
            content = content?.takeIf { it.text.isNotBlank() },
            publishedAt = publishedAt,
            updatedAt = null,
        )
    }

    private fun parseAtom(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): ParsedFeed {
        var title: String? = null
        var subtitle: FeedContent? = null
        var updatedAt: Instant? = null
        val links = mutableListOf<AtomLink>()
        val items = mutableListOf<ParsedFeedItem>()

        var remainingElementDepth = 1

        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (remainingElementDepth != 1) {
                        remainingElementDepth++
                        continue
                    }
                    when (reader.localName) {
                        "entry" -> {
                            if (items.size >= limits.maxItems) break
                            items.add(parseAtomEntry(reader = reader, limits = limits))
                        }

                        "title" -> {
                            title = FeedText.singleLine(readTextConstruct(reader, limits).toPlainText())
                        }

                        "subtitle" -> {
                            subtitle = readTextConstruct(reader, limits)
                        }

                        "link" -> {
                            links.add(readAtomLink(reader))
                        }

                        "updated" -> {
                            updatedAt = FeedDates.parse(readTextContent(reader, limits))
                        }

                        else -> {
                            remainingElementDepth++
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    remainingElementDepth--
                }
            }
        }

        return ParsedFeed(
            format = FeedFormat.ATOM_1_0,
            title = title?.takeIf { it.isNotEmpty() },
            link = chooseAtomLink(links),
            description = subtitle?.takeIf { it.text.isNotBlank() },
            updatedAt = updatedAt,
            items = items,
        )
    }

    private fun parseAtomEntry(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): ParsedFeedItem {
        var id: String? = null
        var title: String? = null
        var summary: FeedContent? = null
        var content: FeedContent? = null
        var publishedAt: Instant? = null
        var updatedAt: Instant? = null
        val links = mutableListOf<AtomLink>()

        var remainingElementDepth = 1

        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (remainingElementDepth != 1) {
                        remainingElementDepth++
                        continue
                    }
                    when (reader.localName) {
                        "id" -> id = readTextContent(reader, limits).trim().takeIf { it.isNotEmpty() }
                        "title" -> title = FeedText.singleLine(readTextConstruct(reader, limits).toPlainText())
                        "summary" -> summary = readTextConstruct(reader, limits)
                        "content" -> content = readTextConstruct(reader, limits)
                        "link" -> links.add(readAtomLink(reader))
                        "published" -> publishedAt = FeedDates.parse(readTextContent(reader, limits))
                        "updated" -> updatedAt = FeedDates.parse(readTextContent(reader, limits))
                        else -> remainingElementDepth++
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    remainingElementDepth--
                }
            }
        }

        return ParsedFeedItem(
            id = id,
            title = title?.takeIf { it.isNotEmpty() },
            link = chooseAtomLink(links),
            summary = summary?.takeIf { it.text.isNotBlank() },
            content = content?.takeIf { it.text.isNotBlank() },
            // published が無い配信元がある。その場合は updated を公開日時として扱う
            publishedAt = publishedAt ?: updatedAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Atom の `link` は属性だけの要素。中身は無いので属性を読んで閉じる。
     */
    private fun readAtomLink(reader: XMLStreamReader): AtomLink {
        val link =
            AtomLink(
                href = reader.getAttributeValue(null, "href")?.trim().orEmpty(),
                // rel の既定値は alternate（記事そのものの URL）
                rel = reader.getAttributeValue(null, "rel")?.trim()?.lowercase() ?: "alternate",
                type = reader.getAttributeValue(null, "type")?.trim()?.lowercase(),
            )
        skipElement(reader)
        return link
    }

    /**
     * 記事の URL に使う `link` を選ぶ。
     *
     * `rel="alternate"` が記事そのものを指す。`enclosure`（添付ファイル）や
     * `self`（フィード自身の URL）を掴むと、記事とは別のものへ誘導することになる。
     * HTML を指すものを優先し、無ければ alternate の先頭を採る。
     */
    private fun chooseAtomLink(links: List<AtomLink>): String? {
        val alternates = links.filter { it.rel == "alternate" && it.href.isNotEmpty() }
        return alternates.firstOrNull { it.type == null || it.type.contains("html") }?.href
            ?: alternates.firstOrNull()?.href
    }

    /**
     * Atom の text construct（`title` / `summary` / `content`）を読む。
     *
     * `type` 属性で中身の種類が変わる。
     *
     * - `text`（既定）— プレーンテキスト
     * - `html` — HTML をエスケープして入れたもの
     * - `xhtml` — XML の子要素としてそのまま入れたもの。ここだけ読み方が違う
     *
     * `content` の `type` にはメディアタイプ（`text/html` など）も入りうる。
     */
    private fun readTextConstruct(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): FeedContent {
        val type = reader.getAttributeValue(null, "type")?.trim()?.lowercase()

        return when {
            type == "xhtml" -> {
                FeedContent(
                    text = readInnerXml(reader = reader, limits = limits),
                    type = FeedContent.Type.HTML,
                )
            }

            type != null && type.contains("html") -> {
                FeedContent(
                    text = readTextContent(reader, limits),
                    type = FeedContent.Type.HTML,
                )
            }

            else -> {
                FeedContent(
                    text = readTextContent(reader, limits),
                    type = FeedContent.Type.TEXT,
                )
            }
        }
    }

    /**
     * いま開いている要素の文字列としての中身を読み、その要素の終わりまで進める。
     *
     * `XMLStreamReader.getElementText()` を使わないのは、子要素があると例外を
     * 投げるため。壊れたフィードで例外にする代わりに、子要素のタグは捨てて
     * テキストだけを繋ぐ。
     */
    private fun readTextContent(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): String {
        val builder = StringBuilder()
        var remainingElementDepth = 1

        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    remainingElementDepth++
                }

                XMLStreamConstants.END_ELEMENT -> {
                    remainingElementDepth--
                }

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                    if (builder.length < limits.maxTextLength) builder.append(reader.text)
                }
            }
        }

        return if (builder.length > limits.maxTextLength) {
            builder.substring(0, limits.maxTextLength)
        } else {
            builder.toString()
        }
    }

    /**
     * いま開いている要素の中身を、タグごと文字列に戻す。Atom の `type="xhtml"` 用。
     *
     * 元の XML をそのまま復元するわけではない（属性の並び順や空白の入れ方は変わる）。
     * 目的はリンクや段落を残したまま [HtmlSanitizer] に渡すことなので、それで足りる。
     */
    private fun readInnerXml(
        reader: XMLStreamReader,
        limits: FeedParserLimits,
    ): String {
        val builder = StringBuilder()
        var remainingElementDepth = 1

        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    remainingElementDepth++
                    if (builder.length >= limits.maxTextLength) continue
                    builder.append('<').append(reader.localName)
                    for (index in 0 until reader.attributeCount) {
                        builder
                            .append(' ')
                            .append(reader.getAttributeLocalName(index))
                            .append("=\"")
                            .append(HtmlSanitizer.escapeText(reader.getAttributeValue(index)))
                            .append('"')
                    }
                    builder.append('>')
                }

                XMLStreamConstants.END_ELEMENT -> {
                    remainingElementDepth--
                    // 一番外側の要素（div など）自身の閉じタグは出力に含めない
                    if (remainingElementDepth > 0 && builder.length < limits.maxTextLength) {
                        builder.append("</").append(reader.localName).append('>')
                    }
                }

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
                    if (builder.length < limits.maxTextLength) {
                        builder.append(HtmlSanitizer.escapeText(reader.text))
                    }
                }
            }
        }

        return builder.toString()
    }

    /** いま開いている要素を、中身ごと終わりまで読み飛ばす */
    private fun skipElement(reader: XMLStreamReader) {
        var remainingElementDepth = 1
        while (reader.hasNext() && remainingElementDepth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> remainingElementDepth++
                XMLStreamConstants.END_ELEMENT -> remainingElementDepth--
            }
        }
    }

    /**
     * 外部を読みに行かない設定の [XMLInputFactory] を作る。
     *
     * フィードの中身は相手のサーバーが返すもので、こちらでは選べない。
     * DTD を有効にしたままだと、入れ子の実体参照でメモリを食い潰す形（billion laughs）と、
     * 外部エンティティでローカルのファイルを読み出す形（XXE）の両方が通る。
     */
    private fun createInputFactory(): XMLInputFactory =
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            // CDATA とテキストが交互に来ると分割して届く。繋いでから受け取る
            setProperty(XMLInputFactory.IS_COALESCING, true)
        }
}

/**
 * 解析の上限。
 *
 * 相手のサーバーが返すものなので、際限なく読むと取り込み側が落ちる。
 * 落とすのは超えたぶんだけで、そこまでに読めたものは返す。
 *
 * @param maxItems 読む記事の数。これを超えた記事は捨てる
 * @param maxTextLength 1 つの要素から読む文字数。超えたぶんは切り捨てる
 */
data class FeedParserLimits(
    val maxItems: Int = 1000,
    val maxTextLength: Int = 256 * 1024,
)

/** フィードとして読めなかった。中身が XML でない場合と、形式が分からない場合 */
class FeedParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Atom の `link` 要素。どれを記事の URL にするかを選ぶための材料 */
private class AtomLink(
    val href: String,
    val rel: String,
    val type: String?,
)
