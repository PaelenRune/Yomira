package com.yomira.feature.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source contract: local JVM tests cannot instantiate Android bitmaps or the Coil composable. */
class ReaderPageBitmapRetentionTest {
    private fun readerSource(): String = sequenceOf(
        File("src/main/java/com/yomira/feature/reader/ReaderPage.kt"),
        File("feature/reader/src/main/java/com/yomira/feature/reader/ReaderPage.kt"),
    ).first { it.exists() }.readText()

    @Test
    fun `ordinary pages never retain a decoded bitmap`() {
        assertTrue(readerSource().contains("decoded = if (guided) (drawable as? BitmapDrawable)?.bitmap else null"))
    }

    @Test
    fun `switching guided view drops previously retained bitmap`() {
        assertTrue(readerSource().contains("remember(page.imageUrl, retry, guided) { mutableStateOf<android.graphics.Bitmap?>(null) }"))
    }
}
