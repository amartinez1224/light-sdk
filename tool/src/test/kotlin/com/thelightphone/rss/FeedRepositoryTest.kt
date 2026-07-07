package com.thelightphone.rss

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
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

        val progress = repository.loadFeeds(listOf(goodFeed, badFeed)).toList()
        val result = progress.toLoadResult()

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

        val progress = repository.loadFeeds(listOf(feed)).toList()
        val result = progress.toLoadResult()

        assertEquals("Newer update", result.items.first().title)
        assertTrue(result.failedFeeds.isEmpty())
    }

    @Test
    fun loadFeedsAppliesItemLimitPerFeed() = runBlocking {
        val feed = FeedDefinition("Limited Feed", "https://example.com/limited.xml", "Limited")
        val repository = FeedRepository(
            fetchText = {
                """
                <rss version="2.0">
                    <channel>
                        <item><title>First update</title></item>
                        <item><title>Second update</title></item>
                    </channel>
                </rss>
                """.trimIndent()
            },
            itemLimit = 1,
        )

        val progress = repository.loadFeeds(listOf(feed)).toList()
        val result = progress.toLoadResult()

        assertEquals(listOf("First update"), result.items.map { it.title })
    }

    @Test
    fun loadFeedsReportsSlowFeedAsFailure() = runBlocking {
        val feed = FeedDefinition("Slow Feed", "https://example.com/slow.xml", "Slow")
        val repository = FeedRepository(
            fetchText = {
                delay(100)
                "<rss version=\"2.0\"><channel /></rss>"
            },
            feedTimeoutMillis = 10,
        )

        val progress = repository.loadFeeds(listOf(feed)).toList()
        val result = progress.toLoadResult()

        assertTrue(result.items.isEmpty())
        assertEquals(listOf("Slow Feed"), result.failedFeeds)
    }
}

private fun List<FeedLoadProgress>.toLoadResult(): FeedLoadResult {
    return FeedLoadResult(
        items = flatMap { it.items }.sortedForDisplay(),
        failedFeeds = filter { it.error != null }.map { it.feed.title },
        successfulFeedCount = count { it.error == null },
    )
}
