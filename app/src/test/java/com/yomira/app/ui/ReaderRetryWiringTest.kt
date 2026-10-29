package com.yomira.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static route contract: supplements the resolver test, not a device/Compose execution test. */
class ReaderRetryWiringTest {
    private fun activity(): String = listOf(
        File("src/main/java/com/yomira/app/MainActivity.kt"),
        File("app/src/main/java/com/yomira/app/MainActivity.kt"),
    ).first { it.isFile }.readText()
        .substringAfter("composable(\"reader/{mangaId}/{chapterId}\"")
        .substringBefore("composable(\"settings\"")

    @Test fun unreachableRouteOffersOneTapRetryThatRestartsResolution() {
        val route = activity()
        assertTrue("Retry must invalidate the resolution effect", route.contains("LaunchedEffect(mangaId, chapterId, retryAttempt)"))
        assertTrue("One click must restart resolution", route.contains("resolving = true; retryAttempt++"))
        assertTrue("Use localized reader retry label", route.contains("R.string.reader_retry"))
    }
    @Test fun existingFailureMessageAndBackActionRemain() {
        val route = activity()
        assertTrue(route.contains("else if (resolvedId == null)"))
        assertTrue(route.contains("R.string.match_unreachable"))
        assertTrue(route.contains("R.string.action_back"))
        assertTrue(route.contains("model.resolveReadingChapter(manga, chapterId)"))
    }
}
