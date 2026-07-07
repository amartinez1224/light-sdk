package com.thelightphone.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

data class CustomFeedInput(
    val title: String,
    val url: String,
)

private data class FeedTextInputRequest(
    val title: String,
    val initialValue: String,
)

class CustomFeedInputScreen(sealedActivity: SealedLightActivity) :
    SimpleLightScreen<CustomFeedInput>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var title by rememberSaveable { mutableStateOf("") }
        var url by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack(null) },
                    ),
                    center = LightTopBarCenter.Text("Custom Feed"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightTextField(
                        label = "Name",
                        value = title,
                        placeholder = "Optional",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    FeedTextInputScreen(
                                        it,
                                        FeedTextInputRequest("Feed Name", title),
                                    )
                                },
                                resultCallback = { title = it },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1f.gridUnitsAsDp()),
                    )
                    LightTextField(
                        label = "URL",
                        value = url,
                        placeholder = "https://example.com/feed.xml",
                        onClick = {
                            navigateTo(
                                screenFactory = {
                                    FeedTextInputScreen(
                                        it,
                                        FeedTextInputRequest("Feed URL", url),
                                    )
                                },
                                resultCallback = { url = it },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 1f.gridUnitsAsDp()),
                    )
                    error?.let {
                        LightText(
                            text = it,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = {
                                if (!url.isSupportedFeedUrl()) {
                                    error = "Enter a valid http or https URL."
                                } else {
                                    goBack(CustomFeedInput(title = title, url = url))
                                }
                            },
                        ),
                    ),
                )
            }
        }
    }
}

private class FeedTextInputScreen(
    sealedActivity: SealedLightActivity,
    private val request: FeedTextInputRequest,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val textState = rememberTextFieldState(request.initialValue)
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = request.title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}
