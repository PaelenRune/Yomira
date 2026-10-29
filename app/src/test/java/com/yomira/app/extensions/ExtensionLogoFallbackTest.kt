package com.yomira.app.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Coil error path of [ExtensionLogoImage]: with the device network
 * unable to reach source sites (B2-1 baseline), every candidate fails and the
 * initials fallback on the OLED plate must remain the rendered state.
 */
class ExtensionLogoFallbackTest {

    private val urls = listOf(
        "https://source.example.org/icon.png",
        "https://source.example.org/favicon.ico",
    )

    @Test
    fun `initials are the default state while the first request is pending`() {
        val fallback = ExtensionLogoFallback(urls)
        assertEquals(urls.first(), fallback.requestUrl)
        assertTrue(fallback.showInitials)
    }

    @Test
    fun `initials show immediately when no candidate url exists`() {
        val fallback = ExtensionLogoFallback(emptyList())
        assertNull(fallback.requestUrl)
        assertTrue(fallback.showInitials)
    }

    @Test
    fun `error advances to the next candidate and keeps initials visible`() {
        val fallback = ExtensionLogoFallback(urls).onImageError()
        assertEquals(urls[1], fallback.requestUrl)
        assertTrue(fallback.showInitials)
    }

    @Test
    fun `exhausting every candidate leaves the initials fallback with no request`() {
        var fallback = ExtensionLogoFallback(urls)
        // More error callbacks than candidates: a duplicated late error must not
        // overshoot the list or resurrect a request.
        repeat(5) { fallback = fallback.onImageError() }
        assertNull(fallback.requestUrl)
        assertTrue(fallback.showInitials)
        assertEquals(urls.size, fallback.attempt)
    }

    @Test
    fun `success hides the initials and keeps the winning request active`() {
        val fallback = ExtensionLogoFallback(urls).onImageError().onImageSuccess()
        assertEquals(urls[1], fallback.requestUrl)
        assertFalse(fallback.showInitials)
    }

    @Test
    fun `a late duplicated error after success cannot remove the loaded image`() {
        val loaded = ExtensionLogoFallback(urls).onImageSuccess()
        val afterLateError = loaded.onImageError()
        assertEquals(loaded, afterLateError)
        assertFalse(afterLateError.showInitials)
        assertEquals(urls.first(), afterLateError.requestUrl)
    }
}
