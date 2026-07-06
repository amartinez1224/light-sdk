package com.thelightphone.rss

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class FeedParser {
    fun parse(feed: FeedDefinition, xml: String): List<FeedItem> {
        val document = documentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        val root = document.documentElement

        return when (root.nodeNameForMatch()) {
            "rss" -> parseRss(feed, root)
            "feed" -> parseAtom(feed, root)
            else -> throw IllegalArgumentException("Unsupported feed format: ${root.nodeName}")
        }
    }

    private fun parseRss(feed: FeedDefinition, root: Element): List<FeedItem> {
        val channel = root.directChild("channel") ?: root
        return channel.directChildren("item").mapIndexed { index, item ->
            val title = item.childText("title").cleanFeedText().ifBlank { "Untitled update" }
            val link = item.childText("link").trim()
            val publishedText = item.childText("pubDate").cleanFeedText()
            val summary = item.childText("description").cleanFeedText()

            FeedItem(
                id = itemId(feed, index, title, link, publishedText),
                sourceTitle = feed.title,
                category = feed.category,
                title = title,
                url = link,
                publishedText = publishedText,
                publishedEpochMillis = parseRssDate(publishedText),
                summary = summary,
            )
        }
    }

    private fun parseAtom(feed: FeedDefinition, root: Element): List<FeedItem> {
        return root.directChildren("entry").mapIndexed { index, entry ->
            val title = entry.childText("title").cleanFeedText().ifBlank { "Untitled update" }
            val link = entry.atomLink()
            val publishedText = entry.childText("updated")
                .ifBlank { entry.childText("published") }
                .cleanFeedText()
            val summary = entry.childText("summary")
                .ifBlank { entry.childText("content") }
                .cleanFeedText()

            FeedItem(
                id = itemId(feed, index, title, link, publishedText),
                sourceTitle = feed.title,
                category = feed.category,
                title = title,
                url = link,
                publishedText = publishedText,
                publishedEpochMillis = parseAtomDate(publishedText),
                summary = summary,
            )
        }
    }

    private fun Element.atomLink(): String {
        val preferred = directChildren("link").firstOrNull { link ->
            val rel = link.getAttribute("rel")
            rel.isBlank() || rel == "alternate"
        } ?: directChild("link")

        return preferred?.getAttribute("href")?.takeIf { it.isNotBlank() }
            ?: preferred?.textContent?.trim()
            ?: ""
    }

    private fun itemId(
        feed: FeedDefinition,
        index: Int,
        title: String,
        link: String,
        publishedText: String,
    ): String {
        return link.ifBlank {
            "${feed.url}#$index-${title.hashCode()}-${publishedText.hashCode()}"
        }
    }
}

internal fun String.cleanFeedText(): String {
    if (isBlank()) return ""

    return this
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#8217;", "'")
        .replace("&#8211;", "-")
        .replace("&#8212;", "-")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun documentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        isExpandEntityReferences = false
    }
}

private fun parseRssDate(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun parseAtomDate(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { ZonedDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}

private fun Element.childText(name: String): String {
    return directChild(name)?.textContent?.trim().orEmpty()
}

private fun Element.directChild(name: String): Element? {
    return directChildren(name).firstOrNull()
}

private fun Element.directChildren(name: String): List<Element> {
    val children = childNodes
    val nameForMatch = name.lowercase()
    return buildList {
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeNameForMatch() == nameForMatch) {
                add(child as Element)
            }
        }
    }
}

private fun Node.nodeNameForMatch(): String {
    return (localName ?: nodeName.substringAfter(':')).lowercase()
}
