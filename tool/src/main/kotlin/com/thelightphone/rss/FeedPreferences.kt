package com.thelightphone.rss

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

class FeedPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadCustomFeeds(): List<FeedDefinition> {
        val stored = dataStore.data.first()[FeedPreferences.CUSTOM_FEEDS_JSON] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredCustomFeed>>(stored)
                .mapNotNull { it.toFeedDefinitionOrNull() }
        }.getOrDefault(emptyList())
    }

    suspend fun saveCustomFeeds(feeds: List<FeedDefinition>) {
        val stored = feeds
            .filter { it.custom }
            .map { StoredCustomFeed(title = it.title, url = it.url) }
        dataStore.edit { prefs ->
            prefs[FeedPreferences.CUSTOM_FEEDS_JSON] = json.encodeToString(stored)
        }
    }
}

internal object FeedPreferences {
    val CUSTOM_FEEDS_JSON = stringPreferencesKey("custom_feeds_json")
}

internal fun StoredCustomFeed.toFeedDefinitionOrNull(): FeedDefinition? {
    val normalizedUrl = url.trim()
    if (!normalizedUrl.isSupportedFeedUrl()) return null
    val normalizedTitle = title.trim().ifBlank { normalizedUrl.hostLikeTitle() }
    return FeedDefinition(
        title = normalizedTitle,
        url = normalizedUrl,
        category = "Custom",
        custom = true,
    )
}

internal fun String.isSupportedFeedUrl(): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}

internal fun String.hostLikeTitle(): String {
    val uri = runCatching { URI(trim()) }.getOrNull()
    return uri?.host?.takeIf { it.isNotBlank() } ?: "Custom Feed"
}
