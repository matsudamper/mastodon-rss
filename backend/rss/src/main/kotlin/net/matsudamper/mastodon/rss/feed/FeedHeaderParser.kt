package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * フィードが名乗るプロフィールヘッダー向けの画像を読む。
 *
 * WebFeeds 拡張の `webfeeds:cover` を先に見て、無ければ `webfeeds:logo` を使う。
 * cover は横長のカバー画像、logo は正方形に近いロゴとして書かれるので、
 * 横長を先に採る。Atom 標準の `logo` はアイコン側で使うので、ここでは見ない。
 */
object FeedHeaderParser {
    fun parse(bytes: ByteArray): String? {
        val reader = createInputFactory().createXMLStreamReader(ByteArrayInputStream(bytes))
        try {
            return parse(reader)
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun parse(reader: XMLStreamReader): String? {
        var logo: String? = null
        val path = ArrayDeque<ElementName>()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val name = ElementName(localName = reader.localName, namespace = reader.namespaceURI.orEmpty())
                    if (isFeedLevel(path) && name.namespace == WEBFEEDS_NAMESPACE) {
                        when (name.localName) {
                            "cover" -> {
                                val image = reader.getAttributeValue(null, "image")?.trim()
                                skipElement(reader)
                                if (!image.isNullOrEmpty()) return image
                                continue
                            }

                            "logo" -> {
                                val value = readTextContent(reader).trim()
                                if (logo == null && value.isNotEmpty()) logo = value
                                continue
                            }
                        }
                    }
                    path.addLast(name)
                }

                XMLStreamConstants.END_ELEMENT -> path.removeLastOrNull()
            }
        }
        return logo
    }

    /**
     * フィード全体を指す要素の直下か。
     *
     * WebFeeds の要素は記事の中にも書けるので、局所名だけで拾うと記事側の画像を
     * フィードの画像として扱う。RSS 2.0 の `rss > channel`、RSS 1.0 の
     * `rdf:RDF > rss:channel`、Atom の `feed` の直下だけを通す
     */
    private fun isFeedLevel(path: ArrayDeque<ElementName>): Boolean {
        if (path.size == 1) {
            val root = path.first()
            return root.localName == "feed" && root.namespace == ATOM_NAMESPACE
        }
        if (path.size != 2) return false

        val root = path.first()
        val parent = path.last()
        val rss2 =
            root.localName == "rss" && root.namespace.isEmpty() &&
                parent.localName == "channel" && parent.namespace.isEmpty()
        val rss1 =
            root.localName == "RDF" && root.namespace == RDF_NAMESPACE &&
                parent.localName == "channel" && parent.namespace == RSS1_NAMESPACE
        return rss2 || rss1
    }

    private fun readTextContent(reader: XMLStreamReader): String {
        val builder = StringBuilder()
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> builder.append(reader.text)
            }
        }
        return builder.toString()
    }

    private fun skipElement(reader: XMLStreamReader) {
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }

    private fun createInputFactory(): XMLInputFactory =
        XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.IS_COALESCING, true)
        }

    private data class ElementName(
        val localName: String,
        val namespace: String,
    )

    private const val WEBFEEDS_NAMESPACE = "http://webfeeds.org/rss/1.0"
    private const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    private const val RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    private const val RSS1_NAMESPACE = "http://purl.org/rss/1.0/"
}
