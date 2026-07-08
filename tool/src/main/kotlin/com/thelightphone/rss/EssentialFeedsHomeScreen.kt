package com.thelightphone.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class EssentialFeedsHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, EssentialFeedsViewModel>(sealedActivity) {

    override val viewModelClass: Class<EssentialFeedsViewModel>
        get() = EssentialFeedsViewModel::class.java

    override fun createViewModel() = EssentialFeedsViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val state by viewModel.uiState.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        var removalCandidate by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        center = LightTopBarCenter.Text("Essential Feeds"),
                        rightButton = LightBarButton.Text(
                            text = "ADD",
                            onClick = {
                                navigateTo(
                                    screenFactory = ::CustomFeedInputScreen,
                                    resultCallback = { input -> viewModel.addCustomFeed(input) },
                                )
                            },
                        ),
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )

                    SourceNavigationBar(
                        state = state,
                        onPreviousSource = viewModel::showPreviousSource,
                        onNextSource = viewModel::showNextSource,
                    )

                    HomeContent(
                        state = state,
                        onOpenItem = { item ->
                            navigateTo(screenFactory = {
                                EssentialFeedDetailScreen(it, item)
                            })
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )

                    LightBottomBar(
                        items = buildList {
                            add(
                                LightBarButton.Text(
                                    text = if (state.refreshing) "UPDATING" else "REFRESH",
                                    onClick = { viewModel.refresh() },
                                ),
                            )
                            state.selectedRemovableSourceTitle?.let { sourceTitle ->
                                add(
                                    LightBarButton.Text(
                                        text = "REMOVE",
                                        onClick = { removalCandidate = sourceTitle },
                                    ),
                                )
                            }
                            if (state.hasMoreVisibleItems()) {
                                add(
                                    LightBarButton.Text(
                                        text = "MORE",
                                        onClick = { viewModel.loadMore() },
                                    ),
                                )
                            }
                        },
                    )
                }

                removalCandidate?.let { sourceTitle ->
                    RemoveFeedConfirmation(
                        sourceTitle = sourceTitle,
                        onCancel = { removalCandidate = null },
                        onRemove = {
                            removalCandidate = null
                            viewModel.removeSelectedFeed()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoveFeedConfirmation(
    sourceTitle: String,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "Remove $sourceTitle?",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.Text(
                    text = "CANCEL",
                    onClick = onCancel,
                ),
                LightBarButton.Text(
                    text = "REMOVE",
                    onClick = onRemove,
                ),
            ),
        )
    }
}

@Composable
private fun SourceNavigationBar(
    state: EssentialFeedsUiState,
    onPreviousSource: () -> Unit,
    onNextSource: () -> Unit,
) {
    LightTopBar(
        leftButton = if (state.canMoveToPreviousSource()) {
            LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onPreviousSource,
                contentDescription = "Previous source",
            )
        } else {
            null
        },
        center = LightTopBarCenter.Text(state.selectedSourceTitle ?: "All Sources"),
        rightButton = if (state.canMoveToNextSource()) {
            LightBarButton.LightIcon(
                icon = LightIcons.ARROW_RIGHT,
                onClick = onNextSource,
                contentDescription = "Next source",
            )
        } else {
            null
        },
        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
    )
}

@Composable
private fun HomeContent(
    state: EssentialFeedsUiState,
    onOpenItem: (FeedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filteredItems = state.filteredItems()
    val visibleItems = filteredItems.take(state.visibleItemLimit)
    when {
        state.loading && state.items.isEmpty() -> CenterMessage(
            text = "Loading updates...",
            modifier = modifier,
        )

        state.items.isEmpty() -> CenterMessage(
            text = state.errorMessage ?: "No updates available.",
            modifier = modifier,
        )

        else -> LightScrollView(
            modifier = modifier.padding(start = 1f.gridUnitsAsDp()),
        ) {
            FeedListSummary(
                state = state,
                filteredCount = filteredItems.size,
                visibleCount = visibleItems.size,
            )
            state.errorMessage?.let { message ->
                LightText(
                    text = message,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            end = 1f.gridUnitsAsDp(),
                            bottom = 0.75f.gridUnitsAsDp(),
                        ),
                )
            }

            visibleItems.groupBySection(state.selectedSourceTitle).forEach { (section, items) ->
                LightText(
                    text = section.uppercase(),
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 0.75f.gridUnitsAsDp(),
                            end = 1f.gridUnitsAsDp(),
                            bottom = 0.25f.gridUnitsAsDp(),
                        ),
                )
                items.forEach { item ->
                    FeedItemRow(
                        item = item,
                        showMetadata = state.selectedSourceTitle == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { onOpenItem(item) }
                            .padding(
                                top = 0.5f.gridUnitsAsDp(),
                                end = 1f.gridUnitsAsDp(),
                                bottom = 0.6f.gridUnitsAsDp(),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedListSummary(
    state: EssentialFeedsUiState,
    filteredCount: Int,
    visibleCount: Int,
) {
    val filterLabel = state.selectedSourceTitle ?: "All sources"
    val sourceCount = state.sourceTitles.size
    val failedCount = state.sourceStatuses.count { !it.successful }
    val sourceText = when {
        sourceCount == 0 -> filterLabel
        state.selectedSourceTitle != null -> filterLabel
        failedCount == 0 -> "$filterLabel / $sourceCount sources"
        else -> "$filterLabel / ${sourceCount - failedCount} of $sourceCount sources"
    }

    LightText(
        text = sourceText,
        variant = LightTextVariant.Detail,
        lighten = true,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 1f.gridUnitsAsDp()),
    )
    LightText(
        text = "$visibleCount of $filteredCount updates shown",
        variant = LightTextVariant.Fine,
        lighten = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 0.2f.gridUnitsAsDp(),
                end = 1f.gridUnitsAsDp(),
                bottom = 0.5f.gridUnitsAsDp(),
            ),
    )
}

@Composable
private fun FeedItemRow(
    item: FeedItem,
    showMetadata: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showMetadata) {
            LightText(
                text = item.category,
                variant = LightTextVariant.Fine,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LightText(
            text = item.title,
            variant = LightTextVariant.Copy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (showMetadata) {
                Modifier.padding(top = 0.2f.gridUnitsAsDp())
            } else {
                Modifier
            },
        )
        if (item.publishedText.isNotBlank()) {
            LightText(
                text = item.publishedText,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
            )
        }
    }
}

private fun EssentialFeedsUiState.filteredItems(): List<FeedItem> {
    val sourceTitle = selectedSourceTitle ?: return items
    return items.filter { it.sourceTitle == sourceTitle }
}

private fun EssentialFeedsUiState.hasMoreVisibleItems(): Boolean {
    return filteredItems().size > visibleItemLimit
}

private fun List<FeedItem>.groupBySection(selectedSourceTitle: String?): Map<String, List<FeedItem>> {
    return if (selectedSourceTitle == null) {
        groupBy { it.sourceTitle }
    } else {
        groupBy { it.category }
    }
}

private fun EssentialFeedsUiState.sourceOptionIndex(): Int {
    val sourceIndex = selectedSourceTitle?.let { sourceTitles.indexOf(it) } ?: -1
    return if (sourceIndex >= 0) sourceIndex + 1 else 0
}

private fun EssentialFeedsUiState.canMoveToPreviousSource(): Boolean {
    return sourceOptionIndex() > 0
}

private fun EssentialFeedsUiState.canMoveToNextSource(): Boolean {
    return sourceOptionIndex() < sourceTitles.size
}

@Composable
private fun CenterMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
            lighten = true,
            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
        )
    }
}
