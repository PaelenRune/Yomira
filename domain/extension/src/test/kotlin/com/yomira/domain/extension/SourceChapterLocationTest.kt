package com.yomira.domain.extension

import org.junit.Assert.*
import org.junit.Test

class SourceChapterLocationTest {
    @Test fun preservesOpaqueSourceAndChapterIdentifiers() {
        val location = SourceChapterLocation("extension.package", "source:/id", "/series/chapter 1?lang=tr&title=ü")
        assertEquals(location, SourceChapterLocation.decode(location.encode()))
        assertNull(SourceChapterLocation.decode("https://example.org/chapter"))
    }
}
