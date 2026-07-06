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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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

    override fun createViewModel() = EssentialFeedsViewModel()

    @Composable
    override fun Content() {
        val state by viewModel.uiState.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Essential Feeds"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
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
                    items = listOf(
                        LightBarButton.Text(
                            text = if (state.refreshing) "UPDATING" else "REFRESH",
                            onClick = { viewModel.refresh() },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: EssentialFeedsUiState,
    onOpenItem: (FeedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
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

            state.items.forEach { item ->
                FeedItemRow(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable { onOpenItem(item) }
                        .padding(
                            top = 0.65f.gridUnitsAsDp(),
                            end = 1f.gridUnitsAsDp(),
                            bottom = 0.65f.gridUnitsAsDp(),
                        ),
                )
            }
        }
    }
}

@Composable
private fun FeedItemRow(
    item: FeedItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LightText(
            text = listOf(item.category, item.sourceTitle).joinToString(" / "),
            variant = LightTextVariant.Fine,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LightText(
            text = item.title,
            variant = LightTextVariant.Copy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
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
