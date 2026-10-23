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
        assertTrue(FileRepositoryStore(path).read().isEmpty())
        FileRepositoryStore(path).write(listOf("https://example.org/index.json"))
        assertEquals(listOf("https://example.org/index.json"), FileRepositoryStore(path).read())
    }

    @Test fun `onboarding state including null repo round trips`() = runTest {
        val path = temporary.root.toPath().resolve("preferences.json")
        val state = OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH, true), null, emptyList(), true)
        assertNull(FileOnboardingStore(path).read())
        FileOnboardingStore(path).write(state)
        assertEquals(state, FileOnboardingStore(path).read())
    }
}
