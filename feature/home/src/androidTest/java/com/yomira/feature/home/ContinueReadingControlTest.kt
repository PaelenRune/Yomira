package com.yomira.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ContinueReadingControlTest {
    @get:Rule val compose = createComposeRule()

    private val entry = ContinueReadingUi(
        mangaId = "series", chapterId = "chapter", title = "Test series",
        coverUrl = null, chapterTitle = "Chapter 3", pageIndex = 4, pageCount = 20,
    )

    @Test fun dismissRemovesCardWithoutOpeningReader() {
        val resume = mutableStateOf<ContinueReadingUi?>(entry)
        var opened = false
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(), onQuery = {}, onColumns = {}, onManga = {}, onRetry = {},
                    continueReading = resume.value,
                    onContinueReading = { opened = true },
                    onDismissContinueReading = { resume.value = null },
                )
            }
        }
        compose.onNodeWithContentDescription(HomeCopy().dismissContinueReading).performClick()
        compose.onNodeWithText(entry.title).assertDoesNotExist()
        assertFalse(opened)
    }

    @Test fun longPressOffersDismissAndResetAndResetsSelectedSeries() {
        var resetId: String? = null
        var opened = false
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = HomeUiState(), onQuery = {}, onColumns = {}, onManga = {}, onRetry = {},
                    continueReading = entry,
                    onContinueReading = { opened = true },
                    onResetProgress = { resetId = it },
                )
            }
        }
        compose.onNodeWithText(entry.title).performTouchInput { longClick() }
        compose.onNodeWithText(HomeCopy().dismissContinueReading).assertExists()
        compose.onNodeWithText(HomeCopy().resetProgress).performClick()
        compose.waitForIdle()
        assertEquals(entry.mangaId, resetId)
        assertFalse(opened)
    }
}
