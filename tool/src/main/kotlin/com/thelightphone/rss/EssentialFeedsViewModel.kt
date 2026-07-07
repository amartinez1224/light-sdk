package com.thelightphone.rss

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
    private val repository: FeedRepository = FeedRepository(),
) : LightViewModel<Unit>() {
    private val _uiState = MutableStateFlow(EssentialFeedsUiState())
    val uiState: StateFlow<EssentialFeedsUiState> = _uiState
    private var loadJob: Job? = null

    init {
        refresh(initialLoad = true)
    }

    fun refresh(initialLoad: Boolean = false) {
        val current = _uiState.value
        if (current.loading || current.refreshing) return

        _uiState.update {
            it.copy(
                loading = initialLoad && it.items.isEmpty(),
                refreshing = !initialLoad || it.items.isNotEmpty(),
                errorMessage = null,
            )
        }

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val loadedItems = mutableListOf<FeedItem>()
            val failedFeeds = mutableListOf<String>()
            var completedFeeds = 0
            var successfulFeeds = 0

            try {
                repository.loadFeeds(defaultFeeds).collect { progress ->
                    completedFeeds += 1
                    if (progress.error == null) {
                        successfulFeeds += 1
                        loadedItems += progress.items
                    } else {
                        failedFeeds += progress.feed.title
                    }

                    val hasFreshItems = loadedItems.isNotEmpty()
                    val stillLoadingFeeds = completedFeeds < defaultFeeds.size
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
                    errorMessage = loadResult.errorMessage(),
                )
            }
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
