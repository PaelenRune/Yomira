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

    @Test fun `a fresh install starts without repositories`() = runTest {
        val path = temporary.root.toPath().resolve("repositories.json")
        val repositories = FileRepositoryStore(path).read()

        assertEquals(0, repositories.size)
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

    @Test fun `user configured indexes are preserved without substitution`() = runTest {
        val path = temporary.root.toPath().resolve("legacy-repositories.json")
        val store = FileRepositoryStore(path)
        val custom = "https://example.org/custom.json"
        val urls = listOf("https://example.org/first.json", "https://example.org/second.json", custom)
        store.write(urls)
        assertEquals(urls, FileRepositoryStore(path).read())
    }
}
