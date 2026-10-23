package com.yomira.app.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.yomira.domain.extension.OnboardingState
import com.yomira.domain.extension.OnboardingWizardManager
import com.yomira.domain.extension.ReaderPreferences
import com.yomira.domain.extension.ReadingLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreOnboardingStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun freshInstallRequiresOnboarding() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(File(temporary.root, "fresh.preferences_pb"), scope)
            assertNull(store.read())
        } finally { scope.cancel() }
    }

    @Test fun preferencesAndCompletionSurviveStoreRecreation() = runTest {
        val file = File(temporary.root, "persisted.preferences_pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val state = OnboardingState(ReaderPreferences(ReadingLanguage.ENGLISH, true),
            OnboardingWizardManager.KEIYOUSHI_INDEX, listOf("extension.en"), true)
        try { store(file, firstScope).write(state) } finally { firstScope.cancel(); firstJob.join() }
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try { assertEquals(state, store(file, secondScope).read()) } finally { secondScope.cancel() }
    }

    @Test fun optingOutClearsPreviouslyStoredRepositoryAndAdultConsent() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = store(File(temporary.root, "consent.preferences_pb"), scope)
            store.write(OnboardingState(ReaderPreferences(ReadingLanguage.ENGLISH, true),
                OnboardingWizardManager.KEIYOUSHI_INDEX, listOf("extension.en"), true))
            val optedOut = OnboardingState(ReaderPreferences(ReadingLanguage.TURKISH), null, emptyList(), true)
            store.write(optedOut)
            assertEquals(optedOut, store.read())
            store.write(optedOut.copy(completed = false))
            assertNull(store.read())
        } finally { scope.cancel() }
    }

    private fun store(file: File, scope: CoroutineScope) = DataStoreOnboardingStore(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
    )
}
