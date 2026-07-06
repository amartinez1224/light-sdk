package com.thelightphone.rss

data class FeedDefinition(
    val title: String,
    val url: String,
    val category: String,
)

data class FeedItem(
    val id: String,
    val sourceTitle: String,
    val category: String,
    val title: String,
    val url: String,
    val publishedText: String,
    val publishedEpochMillis: Long?,
    val summary: String,
)

data class EssentialFeedsUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val items: List<FeedItem> = emptyList(),
    val errorMessage: String? = null,
)

val defaultFeeds = listOf(
    FeedDefinition(
        title = "USGS Significant Earthquakes",
        url = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_month.atom",
        category = "Earthquakes",
    ),
    FeedDefinition(
        title = "NHC Atlantic",
        url = "https://www.nhc.noaa.gov/index-at.xml",
        category = "Hurricanes",
    ),
    FeedDefinition(
        title = "NHC Eastern Pacific",
        url = "https://www.nhc.noaa.gov/index-ep.xml",
        category = "Hurricanes",
    ),
    FeedDefinition(
        title = "CDC Online Newsroom",
        url = "https://tools.cdc.gov/api/v2/resources/media/132608.rss",
        category = "Public Health",
    ),
)
