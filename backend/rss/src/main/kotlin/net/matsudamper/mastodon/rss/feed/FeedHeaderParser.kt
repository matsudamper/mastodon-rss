package net.matsudamper.mastodon.rss.feed

import java.io.ByteArrayInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** WebFeeds のカバー画像を読み、無ければ WebFeeds のロゴを返す */
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
        val path = ArrayDeque<String>()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val name = reader.localName
                    val parent = path.lastOrNull()
                    val isFeedLevel = parent == "channel" || parent == "feed"
                    if (isFeedLevel && reader.namespaceURI == WEBFEEDS_NAMESPACE) {
                        when (name) {
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

    private const val WEBFEEDS_NAMESPACE = "http://webfeeds.org/rss/1.0"
}
