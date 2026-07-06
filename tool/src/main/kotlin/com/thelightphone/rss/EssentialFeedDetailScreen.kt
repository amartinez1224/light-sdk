package com.thelightphone.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
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

class EssentialFeedDetailScreen(
    sealedActivity: SealedLightActivity,
    private val item: FeedItem,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text(item.category),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = item.sourceTitle,
                        variant = LightTextVariant.Fine,
                        lighten = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 1f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = item.title,
                        variant = LightTextVariant.Heading,
                        modifier = Modifier
                            .padding(
                                top = 0.5f.gridUnitsAsDp(),
                                end = 1f.gridUnitsAsDp(),
                            ),
                    )
                    if (item.publishedText.isNotBlank()) {
                        LightText(
                            text = item.publishedText,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier
                                .padding(
                                    top = 0.75f.gridUnitsAsDp(),
                                    end = 1f.gridUnitsAsDp(),
                                ),
                        )
                    }
                    if (item.summary.isNotBlank()) {
                        LightText(
                            text = item.summary,
                            variant = LightTextVariant.Paragraph,
                            modifier = Modifier
                                .padding(
                                    top = 1f.gridUnitsAsDp(),
                                    end = 1f.gridUnitsAsDp(),
                                ),
                        )
                    }
                    if (item.url.isNotBlank()) {
                        LightText(
                            text = item.url,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier
                                .padding(
                                    top = 1f.gridUnitsAsDp(),
                                    end = 1f.gridUnitsAsDp(),
                                    bottom = 1f.gridUnitsAsDp(),
                                ),
                        )
                    }
                }
            }
        }
    }
}
