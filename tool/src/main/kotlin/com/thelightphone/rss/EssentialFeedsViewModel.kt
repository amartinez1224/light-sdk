package com.thelightphone.rss

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EssentialFeedsViewModel(
    dataStore: DataStore<Preferences>,
    private val repository: FeedRepository = FeedRepository(),
) : LightViewModel<Unit>() {
    private val preferencesStore = FeedPreferencesStore(dataStore)
    private val _uiState = MutableStateFlow(EssentialFeedsUiState())
    val uiState: StateFlow<EssentialFeedsUiState> = _uiState
    private var loadJob: Job? = null
    private var activeFeeds: List<FeedDefinition> = defaultFeeds
    private var removedDefaultFeedUrls: Set<String> = emptySet()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val customFeeds = preferencesStore.loadCustomFeeds()
            removedDefaultFeedUrls = preferencesStore.loadRemovedDefaultFeedUrls()
            activeFeeds = buildActiveFeeds(customFeeds, removedDefaultFeedUrls)
            _uiState.update {
                it.copy(
                    customFeeds = customFeeds,
                    sourceTitles = activeFeeds.sourceTitles(),
                )
            }
            refresh(initialLoad = true)
        }
    }

    fun refresh(initialLoad: Boolean = false) {
        val current = _uiState.value
        if (current.loading || current.refreshing) return

        _uiState.update {
            it.copy(
                loading = initialLoad && it.items.isEmpty(),
                refreshing = !initialLoad || it.items.isNotEmpty(),
                errorMessage = null,
                sourceStatuses = emptyList(),
                visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
            )
        }

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val loadedItems = mutableListOf<FeedItem>()
            val failedFeeds = mutableListOf<String>()
            val sourceStatuses = mutableListOf<FeedSourceStatus>()
            var completedFeeds = 0
            var successfulFeeds = 0
            val feedsToLoad = activeFeeds

            try {
                repository.loadFeeds(feedsToLoad).collect { progress ->
                    completedFeeds += 1
                    if (progress.error == null) {
                        successfulFeeds += 1
                        loadedItems += progress.items
                        sourceStatuses += FeedSourceStatus(
                            title = progress.feed.title,
                            category = progress.feed.category,
                            successful = true,
                            itemCount = progress.items.size,
                        )
                    } else {
                        failedFeeds += progress.feed.title
                        sourceStatuses += FeedSourceStatus(
                            title = progress.feed.title,
                            category = progress.feed.category,
                            successful = false,
                        )
                    }

                    val hasFreshItems = loadedItems.isNotEmpty()
                    val stillLoadingFeeds = completedFeeds < feedsToLoad.size
                    val loadResult = FeedLoadResult(
                        items = loadedItems,
                        failedFeeds = failedFeeds,
                        successfulFeedCount = successfulFeeds,
                    )

                    _uiState.update { currentState ->
                        currentState.copy(
                            loading = initialLoad && !hasFreshItems && stillLoadingFeeds,
                            refreshing = stillLoadingFeeds && (!initialLoad || hasFreshItems),
                            items = when {
                                hasFreshItems -> loadedItems.sortedForDisplay()
                                successfulFeeds > 0 -> emptyList()
                                initialLoad -> emptyList()
                                else -> currentState.items
                            },
                            sourceStatuses = sourceStatuses.toList(),
                            errorMessage = loadResult.errorMessage(),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { currentState ->
                    currentState.copy(
                        loading = false,
                        refreshing = false,
                        errorMessage = error.message ?: "Unable to load feeds.",
                        sourceStatuses = sourceStatuses.toList(),
                    )
                }
                return@launch
            }

            _uiState.update { currentState ->
                val loadResult = FeedLoadResult(
                    items = loadedItems,
                    failedFeeds = failedFeeds,
                    successfulFeedCount = successfulFeeds,
                )
                currentState.copy(
                    loading = false,
                    refreshing = false,
                    items = when {
                        loadedItems.isNotEmpty() -> loadedItems.sortedForDisplay()
                        successfulFeeds > 0 -> emptyList()
                        initialLoad -> emptyList()
                        else -> currentState.items
                    },
                    sourceStatuses = sourceStatuses.toList(),
                    errorMessage = loadResult.errorMessage(),
                )
            }
        }
    }

    fun loadMore() {
        _uiState.update {
            it.copy(visibleItemLimit = it.visibleItemLimit + VISIBLE_ITEM_LIMIT_INCREMENT)
        }
    }

    fun showPreviousSource() {
        _uiState.update { state ->
            val titles = activeFeeds.sourceTitles()
            val currentIndex = state.sourceOptionIndex(titles)
            val previousIndex = (currentIndex - 1).coerceAtLeast(0)
            state.copy(
                selectedSourceTitle = titles.titleAtOptionIndex(previousIndex),
                visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
            )
        }
    }

    fun showNextSource() {
        _uiState.update { state ->
            val titles = activeFeeds.sourceTitles()
            val currentIndex = state.sourceOptionIndex(titles)
            val nextIndex = (currentIndex + 1).coerceAtMost(titles.size)
            state.copy(
                selectedSourceTitle = titles.titleAtOptionIndex(nextIndex),
                visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
            )
        }
    }

    fun addCustomFeed(input: CustomFeedInput) {
        val feed = StoredCustomFeed(
            title = input.title.trim(),
            url = input.url.trim(),
        ).toFeedDefinitionOrNull() ?: run {
            _uiState.update { it.copy(errorMessage = "Enter a valid http or https feed URL.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentCustomFeeds = _uiState.value.customFeeds
            val withoutDuplicate = currentCustomFeeds.filterNot {
                it.url.equals(feed.url, ignoreCase = true)
            }
            val updatedCustomFeeds = withoutDuplicate + feed
            preferencesStore.saveCustomFeeds(updatedCustomFeeds)
            activeFeeds = buildActiveFeeds(updatedCustomFeeds, removedDefaultFeedUrls)
            _uiState.update {
                it.copy(
                    customFeeds = updatedCustomFeeds,
                    sourceTitles = activeFeeds.sourceTitles(),
                    selectedSourceTitle = null,
                    visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
                    errorMessage = null,
                )
            }
            refresh(initialLoad = false)
        }
    }

    fun removeSelectedFeed() {
        val selectedTitle = _uiState.value.selectedRemovableSourceTitle ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val selectedFeed = activeFeeds.firstOrNull { it.title == selectedTitle } ?: return@launch
            val updatedCustomFeeds = if (selectedFeed.custom) {
                _uiState.value.customFeeds.withoutCustomFeedTitle(selectedTitle)
            } else {
                _uiState.value.customFeeds
            }
            val updatedRemovedDefaultFeedUrls = if (selectedFeed.custom) {
                removedDefaultFeedUrls
            } else {
                removedDefaultFeedUrls + selectedFeed.url
            }
            preferencesStore.saveCustomFeeds(updatedCustomFeeds)
            preferencesStore.saveRemovedDefaultFeedUrls(updatedRemovedDefaultFeedUrls)
            removedDefaultFeedUrls = updatedRemovedDefaultFeedUrls
            activeFeeds = buildActiveFeeds(updatedCustomFeeds, removedDefaultFeedUrls)
            _uiState.update {
                it.copy(
                    customFeeds = updatedCustomFeeds,
                    sourceTitles = activeFeeds.sourceTitles(),
                    selectedSourceTitle = null,
                    visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
                    items = emptyList(),
                    sourceStatuses = emptyList(),
                    errorMessage = null,
                )
            }
            refresh(initialLoad = false)
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        repository.close()
        super.onCleared()
    }
}

private fun FeedLoadResult.errorMessage(): String? {
    if (failedFeeds.isEmpty()) return null
    if (successfulFeedCount == 0) return "Unable to load feeds."

    val failed = failedFeeds.take(2).joinToString(", ")
    val suffix = if (failedFeeds.size > 2) " and ${failedFeeds.size - 2} more" else ""
    return "Some feeds could not load: $failed$suffix"
}

private fun List<FeedDefinition>.sourceTitles(): List<String> {
    return map { it.title }.distinct()
}

private fun EssentialFeedsUiState.sourceOptionIndex(sourceTitles: List<String>): Int {
    val sourceIndex = selectedSourceTitle?.let { sourceTitles.indexOf(it) } ?: -1
    return if (sourceIndex >= 0) sourceIndex + 1 else 0
}

private fun List<String>.titleAtOptionIndex(index: Int): String? {
    return if (index == 0) null else getOrNull(index - 1)
}

internal fun List<FeedDefinition>.withoutCustomFeedTitle(title: String): List<FeedDefinition> {
    return filterNot { it.custom && it.title == title }
}

internal fun buildActiveFeeds(
    customFeeds: List<FeedDefinition>,
    removedDefaultFeedUrls: Set<String>,
): List<FeedDefinition> {
    return defaultFeeds.filterNot { it.url in removedDefaultFeedUrls } + customFeeds
}
