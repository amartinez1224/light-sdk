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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val customFeeds = preferencesStore.loadCustomFeeds()
            activeFeeds = defaultFeeds + customFeeds
            _uiState.update { it.copy(customFeeds = customFeeds) }
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

    fun cycleCategoryFilter() {
        _uiState.update { state ->
            val categories = state.availableCategories()
            val currentIndex = state.selectedCategory?.let { categories.indexOf(it) } ?: -1
            val nextCategory = categories.getOrNull(currentIndex + 1)
            state.copy(
                selectedCategory = nextCategory,
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
            activeFeeds = defaultFeeds + updatedCustomFeeds
            _uiState.update {
                it.copy(
                    customFeeds = updatedCustomFeeds,
                    selectedCategory = null,
                    visibleItemLimit = INITIAL_VISIBLE_ITEM_LIMIT,
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

private fun EssentialFeedsUiState.availableCategories(): List<String> {
    return (items.map { it.category } + customFeeds.map { it.category })
        .distinct()
        .sorted()
}
