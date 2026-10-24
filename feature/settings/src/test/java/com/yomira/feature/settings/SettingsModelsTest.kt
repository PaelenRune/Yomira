package com.yomira.feature.settings

import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.MainDestination
import com.yomira.domain.extension.OnboardingWizardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelsTest {
    @Test fun `bottom bar keeps every destination while the vault is visible`() {
        assertEquals(MainDestination.entries.toList(), visibleBottomDestinations(vaultVisible = true))
    }

    @Test fun `hiding the vault removes its tab completely`() {
        val destinations = visibleBottomDestinations(vaultVisible = false)
        assertFalse(MainDestination.VAULT in destinations)
        assertEquals(
            listOf(MainDestination.HOME, MainDestination.DISCOVER, MainDestination.LIBRARY),
            destinations,
        )
        // No placeholder, lock icon or disabled entry may survive the hide.
        assertTrue(destinations.none { it.route == MainDestination.VAULT.route })
    }

    @Test fun `fresh settings keep the pre-settings security posture`() {
        val defaults = AppSettings()
        assertTrue(defaults.vaultVisible)
        assertTrue(defaults.requireDeviceLock)
        assertTrue(defaults.lockOnBackground)
        assertEquals(ReadingMode.MANGA_RTL, defaults.readingMode)
    }

    @Test fun `curated repositories point at distinct https indexes`() {
        val urls = CuratedRepositories.ALL.map { it.indexUrl }
        val ids = CuratedRepositories.ALL.map { it.id }
        assertEquals(urls.size, urls.distinct().size)
        assertEquals(ids.size, ids.distinct().size)
        urls.forEach { url -> assertTrue(url.startsWith("https://")) }
    }

    @Test fun `universal switch mirrors the onboarding registration url`() {
        // If these diverge the switch would show "off" for a repo onboarding already registered.
        assertEquals(OnboardingWizardManager.KEIYOUSHI_INDEX, CuratedRepositories.UNIVERSAL.indexUrl)
    }

    @Test fun `custom repositories are registered urls minus the curated ones`() {
        val registered = listOf(
            CuratedRepositories.UNIVERSAL.indexUrl,
            CuratedRepositories.WESTERN_COMICS.indexUrl,
            "https://example.org/index.min.json",
        )
        assertEquals(listOf("https://example.org/index.min.json"), customRepositories(registered))
        assertEquals(emptyList<String>(), customRepositories(emptyList()))
    }

    @Test fun `language tags resolve to the two supported values`() {
        assertEquals(AppLanguage.TURKISH, AppLanguage.fromTag("tr"))
        assertEquals(AppLanguage.TURKISH, AppLanguage.fromTag("tr-TR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("de"))
        assertNotEquals(AppLanguage.TURKISH, AppLanguage.ENGLISH)
    }

    @Test fun `cache sizes render deterministically`() {
        assertEquals("0 B", formatCacheSize(0))
        assertEquals("512 B", formatCacheSize(512))
        assertEquals("1.5 KB", formatCacheSize(1_536))
        assertEquals("2.0 MB", formatCacheSize(2 * 1_048_576))
        assertNull(formatCacheSize(-1).takeIf { it.isEmpty() })
    }

    @Test fun `update statuses are distinguishable`() {
        assertNotEquals(UpdateCheckStatus.Idle, UpdateCheckStatus.Checking)
        assertNotEquals(UpdateCheckStatus.UpToDate, UpdateCheckStatus.Available("v1", "https://example.org/a.apk", ""))
        assertEquals(
            UpdateCheckStatus.Available("v1", "https://example.org/a.apk", "notes"),
            UpdateCheckStatus.Available("v1", "https://example.org/a.apk", "notes"),
        )
    }
}
