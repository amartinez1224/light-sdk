package com.thelightphone.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedPreferencesTest {
    @Test
    fun storedCustomFeedNormalizesIntoCustomDefinition() {
        val definition = StoredCustomFeed(
            title = " Local Alerts ",
            url = " https://example.com/feed.xml ",
        ).toFeedDefinitionOrNull()

        assertEquals("Local Alerts", definition?.title)
        assertEquals("https://example.com/feed.xml", definition?.url)
        assertEquals("Custom", definition?.category)
        assertEquals(true, definition?.custom)
    }

    @Test
    fun storedCustomFeedUsesHostWhenTitleIsBlank() {
        val definition = StoredCustomFeed(
            title = "",
            url = "https://example.com/news/rss",
        ).toFeedDefinitionOrNull()

        assertEquals("example.com", definition?.title)
    }

    @Test
    fun storedCustomFeedRejectsUnsupportedUrls() {
        val definition = StoredCustomFeed(
            title = "Not a feed",
            url = "ftp://example.com/feed.xml",
        ).toFeedDefinitionOrNull()

        assertNull(definition)
    }

    @Test
    fun supportedFeedUrlsAllowHttpAndHttpsOnly() {
        assertTrue("https://example.com/feed.xml".isSupportedFeedUrl())
        assertTrue("http://example.com/feed.xml".isSupportedFeedUrl())
        assertFalse("feed://example.com/feed.xml".isSupportedFeedUrl())
        assertFalse("example.com/feed.xml".isSupportedFeedUrl())
    }
}
