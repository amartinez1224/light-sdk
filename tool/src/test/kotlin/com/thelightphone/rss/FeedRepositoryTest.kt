package com.thelightphone.rss

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedRepositoryTest {
    @Test
    fun loadFeedsKeepsSuccessfulItemsWhenOneFeedFails() = runBlocking {
        val goodFeed = FeedDefinition("Good Feed", "https://example.com/good.xml", "Good")
        val badFeed = FeedDefinition("Bad Feed", "https://example.com/bad.xml", "Bad")
        val repository = FeedRepository(
            fetchText = { url ->
                when (url) {
                    goodFeed.url -> """
                        <rss version="2.0">
                            <channel>
                                <item>
                                    <title>Good update</title>
                                    <link>https://example.com/good-update</link>
                                    <pubDate>Mon, 06 Jul 2026 10:30:00 GMT</pubDate>
                                </item>
                            </channel>
                        </rss>
                    """.trimIndent()

                    else -> error("Network failed")
                }
            },
        )

        val result = repository.loadFeeds(listOf(goodFeed, badFeed))

        assertEquals(1, result.items.size)
        assertEquals("Good update", result.items.first().title)
        assertEquals(listOf("Bad Feed"), result.failedFeeds)
    }

    @Test
    fun loadFeedsSortsParseableDatesNewestFirst() = runBlocking {
        val feed = FeedDefinition("Good Feed", "https://example.com/good.xml", "Good")
        val repository = FeedRepository(
            fetchText = {
                """
                <rss version="2.0">
                    <channel>
                        <item>
                            <title>Older update</title>
                            <pubDate>Mon, 06 Jul 2026 10:30:00 GMT</pubDate>
                        </item>
                        <item>
                            <title>Newer update</title>
                            <pubDate>Mon, 06 Jul 2026 11:30:00 GMT</pubDate>
                        </item>
                    </channel>
                </rss>
                """.trimIndent()
            },
        )

        val result = repository.loadFeeds(listOf(feed))

        assertEquals("Newer update", result.items.first().title)
        assertTrue(result.failedFeeds.isEmpty())
    }
}
