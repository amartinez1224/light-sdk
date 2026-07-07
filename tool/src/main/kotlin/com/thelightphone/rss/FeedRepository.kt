package com.thelightphone.rss

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val DEFAULT_FEED_TIMEOUT_MILLIS = 10_000L

data class FeedLoadResult(
    val items: List<FeedItem>,
    val failedFeeds: List<String>,
    val successfulFeedCount: Int = if (items.isEmpty()) 0 else 1,
)

data class FeedLoadProgress(
    val feed: FeedDefinition,
    val items: List<FeedItem> = emptyList(),
    val error: Throwable? = null,
)

class FeedRepository(
    private val fetchText: (suspend (String) -> String)? = null,
    private val parser: FeedParser = FeedParser(),
    private val feedTimeoutMillis: Long = DEFAULT_FEED_TIMEOUT_MILLIS,
) {
    private val client: HttpClient? = if (fetchText == null) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = feedTimeoutMillis
                connectTimeoutMillis = feedTimeoutMillis
                socketTimeoutMillis = feedTimeoutMillis
            }
        }
    } else {
        null
    }

    fun loadFeeds(feeds: List<FeedDefinition>): Flow<FeedLoadProgress> = channelFlow {
        feeds.forEach { feed ->
            launch {
                try {
                    val items = withTimeout(feedTimeoutMillis) {
                        parser.parse(feed, fetchFeedText(feed.url))
                    }
                    send(
                        FeedLoadProgress(
                            feed = feed,
                            items = items,
                        ),
                    )
                } catch (error: TimeoutCancellationException) {
                    send(
                        FeedLoadProgress(
                            feed = feed,
                            error = error,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    send(
                        FeedLoadProgress(
                            feed = feed,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun fetchFeedText(url: String): String {
        fetchText?.let { return it(url) }
        val response = requireNotNull(client).get(url)
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    fun close() {
        client?.close()
    }
}

internal fun List<FeedItem>.sortedForDisplay(): List<FeedItem> {
    return withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<FeedItem>> {
                it.value.publishedEpochMillis ?: Long.MIN_VALUE
            }.thenBy { it.index },
        )
        .map { it.value }
}
