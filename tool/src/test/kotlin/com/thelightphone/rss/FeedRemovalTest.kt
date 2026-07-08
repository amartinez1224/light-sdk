package com.thelightphone.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedRemovalTest {
    private val defaultFeed = FeedDefinition(
        title = "Default Feed",
        url = "https://example.com/default.xml",
        category = "Default",
    )
    private val customFeed = FeedDefinition(
        title = "Custom Feed",
        url = "https://example.com/custom.xml",
        category = "Custom",
        custom = true,
    )

    @Test
    fun selectedCustomSourceTitleReturnsOnlyCustomSources() {
        val state = EssentialFeedsUiState(
            customFeeds = listOf(customFeed),
            selectedSourceTitle = customFeed.title,
        )

        assertEquals(customFeed.title, state.selectedCustomSourceTitle)
    }

    @Test
    fun selectedCustomSourceTitleIgnoresDefaultSources() {
        val state = EssentialFeedsUiState(
            customFeeds = listOf(customFeed),
            selectedSourceTitle = defaultFeed.title,
        )

        assertNull(state.selectedCustomSourceTitle)
    }

    @Test
    fun withoutCustomFeedTitleRemovesOnlyMatchingCustomFeed() {
        val otherCustomFeed = customFeed.copy(
            title = "Other Custom Feed",
            url = "https://example.com/other.xml",
        )

        val result = listOf(defaultFeed, customFeed, otherCustomFeed)
            .withoutCustomFeedTitle(customFeed.title)

        assertEquals(listOf(defaultFeed, otherCustomFeed), result)
    }
}
