package com.yomira.data.extension

import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileExtensionStoresTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `repo consent survives reopening store`() = runTest {
        val path = temporary.root.toPath().resolve("repositories.json")
        // Explicitly empty defaults keep this test about persistence, not seeding.
        assertTrue(FileRepositoryStore(path, defaultRepositories = emptyList()).read().isEmpty())
        FileRepositoryStore(path).write(listOf("https://example.org/index.json"))
        assertEquals(listOf("https://example.org/index.json"), FileRepositoryStore(path).read())
    }

    @Test fun `a fresh install starts with the bundled repositories`() = runTest {
        val path = temporary.root.toPath().resolve("repositories.json")
        val repositories = FileRepositoryStore(path).read()

        assertEquals(1, repositories.size)
        assertTrue(repositories.all { it.startsWith("https://") })
        assertEquals(EmbeddedRepositories.load().map { it.indexUrl }, repositories)
    }

    @Test fun `onboarding state including null repo round trips`() = runTest {
        val path = temporary.root.toPath().resolve("preferences.json")
        val state = OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH, true, true, setOf("futanari", "gore"), setOf("romance")), null, emptyList(), true)
        assertNull(FileOnboardingStore(path).read())
        FileOnboardingStore(path).write(state)
        assertEquals(state, FileOnboardingStore(path).read())
    }

    @Test fun `legacy bundled indexes migrate without conflicting package ownership`() = runTest {
        val path = temporary.root.toPath().resolve("legacy-repositories.json")
        val store = FileRepositoryStore(path)
        val custom = "https://example.org/custom.json"
        store.write(listOf("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
            "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/export/turkish/index.min.json",
            "https://raw.githubusercontent.com/PaelenRune/Yomira/main/repositories/export/global/index.min.json", custom))
        assertEquals(EmbeddedRepositories.load().map { it.indexUrl } + custom, store.read())
    }
}
