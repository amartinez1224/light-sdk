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
    fun selectedRemovableSourceTitleReturnsSelectedActiveSource() {
        val state = EssentialFeedsUiState(
            sourceTitles = listOf(defaultFeed.title, customFeed.title),
            selectedSourceTitle = customFeed.title,
        )

        assertEquals(customFeed.title, state.selectedRemovableSourceTitle)
    }

    @Test
    fun selectedRemovableSourceTitleIgnoresUnknownSources() {
        val state = EssentialFeedsUiState(
            sourceTitles = listOf(customFeed.title),
            selectedSourceTitle = defaultFeed.title,
        )

        assertNull(state.selectedRemovableSourceTitle)
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

    @Test
    fun buildActiveFeedsHidesRemovedDefaultFeeds() {
        val removedDefaultFeed = defaultFeeds.first()

        val result = buildActiveFeeds(
            customFeeds = listOf(customFeed),
            removedDefaultFeedUrls = setOf(removedDefaultFeed.url),
        )

        assertEquals(false, result.any { it.url == removedDefaultFeed.url })
        assertEquals(true, result.contains(customFeed))
    }

    @Test
    fun buildActiveFeedsCanReturnOnlyCustomFeeds() {
        val result = buildActiveFeeds(
            customFeeds = listOf(customFeed),
            removedDefaultFeedUrls = defaultFeeds.map { it.url }.toSet(),
        )

        assertEquals(listOf(customFeed), result)
    }

    @Test
    fun buildActiveFeedsCanReturnEmptyList() {
        val result = buildActiveFeeds(
            customFeeds = emptyList(),
            removedDefaultFeedUrls = defaultFeeds.map { it.url }.toSet(),
        )

        assertEquals(emptyList(), result)
    }
}
