package com.thelightphone.rss

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EssentialFeedsViewModel(
    private val repository: FeedRepository = FeedRepository(),
) : LightViewModel<Unit>() {
    private val _uiState = MutableStateFlow(EssentialFeedsUiState())
    val uiState: StateFlow<EssentialFeedsUiState> = _uiState

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

        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.loadFeeds(defaultFeeds) }

            _uiState.update { currentState ->
                result.fold(
                    onSuccess = { loadResult ->
                        currentState.copy(
                            loading = false,
                            refreshing = false,
                            items = loadResult.items,
                            errorMessage = loadResult.errorMessage(),
                        )
                    },
                    onFailure = { error ->
                        currentState.copy(
                            loading = false,
                            refreshing = false,
                            errorMessage = error.message ?: "Unable to load feeds.",
                        )
                    },
                )
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

private fun FeedLoadResult.errorMessage(): String? {
    if (failedFeeds.isEmpty()) return null
    if (items.isEmpty()) return "Unable to load feeds."

    val failed = failedFeeds.take(2).joinToString(", ")
    val suffix = if (failedFeeds.size > 2) " and ${failedFeeds.size - 2} more" else ""
    return "Some feeds could not load: $failed$suffix"
}
