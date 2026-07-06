package com.thelightphone.rss

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

data class FeedLoadResult(
    val items: List<FeedItem>,
    val failedFeeds: List<String>,
)

class FeedRepository(
    private val fetchText: suspend (String) -> String = defaultFetcher(),
    private val parser: FeedParser = FeedParser(),
) {
    suspend fun loadFeeds(feeds: List<FeedDefinition>): FeedLoadResult {
        val items = mutableListOf<FeedItem>()
        val failedFeeds = mutableListOf<String>()

        feeds.forEach { feed ->
            val result = runCatching {
                parser.parse(feed, fetchText(feed.url))
            }

            result
                .onSuccess { items += it }
                .onFailure { failedFeeds += feed.title }
        }

        val sorted = items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<FeedItem>> {
                    it.value.publishedEpochMillis ?: Long.MIN_VALUE
                }.thenBy { it.index },
            )
            .map { it.value }

        return FeedLoadResult(
            items = sorted,
            failedFeeds = failedFeeds,
        )
    }

    fun close() {
        client?.close()
    }

    companion object {
        private var client: HttpClient? = null

        private fun defaultFetcher(): suspend (String) -> String {
            val httpClient = HttpClient(OkHttp)
            client = httpClient
            return { url ->
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("HTTP ${response.status.value}")
                }
                response.bodyAsText()
            }
        }
    }
}
