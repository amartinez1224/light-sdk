package com.thelightphone.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedParserTest {
    private val parser = FeedParser()
    private val feed = FeedDefinition(
        title = "Test Feed",
        url = "https://example.com/feed.xml",
        category = "Tests",
    )

    @Test
    fun parsesRss20Items() {
        val items = parser.parse(
            feed,
            """
            <rss version="2.0">
                <channel>
                    <title>Example RSS</title>
                    <item>
                        <title>Storm update</title>
                        <link>https://example.com/storm</link>
                        <description><![CDATA[<p>Wind &amp; rain expected.</p>]]></description>
                        <pubDate>Mon, 06 Jul 2026 10:30:00 GMT</pubDate>
                    </item>
                </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals(1, items.size)
        assertEquals("Storm update", items.first().title)
        assertEquals("https://example.com/storm", items.first().url)
        assertEquals("Wind & rain expected.", items.first().summary)
        assertNotNull(items.first().publishedEpochMillis)
    }

    @Test
    fun parsesAtomItems() {
        val items = parser.parse(
            feed,
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Example Atom</title>
                <entry>
                    <title>Earthquake update</title>
                    <link href="https://example.com/quake" />
                    <summary>Magnitude 5.1 reported.</summary>
                    <updated>2026-07-06T12:00:00Z</updated>
                </entry>
            </feed>
            """.trimIndent(),
        )

        assertEquals(1, items.size)
        assertEquals("Earthquake update", items.first().title)
        assertEquals("https://example.com/quake", items.first().url)
        assertEquals("Magnitude 5.1 reported.", items.first().summary)
        assertNotNull(items.first().publishedEpochMillis)
    }

    @Test
    fun handlesMissingOptionalFields() {
        val items = parser.parse(
            feed,
            """
            <rss version="2.0">
                <channel>
                    <item>
                        <title>Brief item</title>
                    </item>
                </channel>
            </rss>
            """.trimIndent(),
        )

        assertEquals(1, items.size)
        assertEquals("Brief item", items.first().title)
        assertEquals("", items.first().url)
        assertEquals("", items.first().publishedText)
        assertEquals(null, items.first().publishedEpochMillis)
        assertEquals("", items.first().summary)
    }

    @Test
    fun stripsBasicHtmlAndEntities() {
        val text = """
            &lt;div&gt;CDC&nbsp;&lt;strong&gt;alert&lt;/strong&gt; &amp; guidance&#8212;updated.&lt;/div&gt;
            <script>ignored()</script>
        """.trimIndent()

        assertEquals("CDC alert & guidance-updated.", text.cleanFeedText())
    }

    @Test
    fun unsupportedFeedThrows() {
        val result = runCatching {
            parser.parse(feed, "<notafeed />")
        }

        assertTrue(result.isFailure)
    }
}
