package com.yomira.core.database

import com.yomira.core.database.mapper.toEntity
import com.yomira.core.database.mapper.toModel
import com.yomira.core.model.Manga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PopularityPersistenceTest {
    @Test fun `global reader counts survive the Room model round trip`() {
        val manga = Manga("anilist:1", "A globally popular series", sourceId = "anilist", popularity = 1_234_567)
        assertEquals(manga, manga.toEntity().toModel())
    }

    @Test fun `legacy unknown counts stay unknown rather than fabricated`() {
        val manga = Manga("test:1", "A legacy series", sourceId = "test")
        assertNull(manga.toEntity().toModel().popularity)
    }
}
