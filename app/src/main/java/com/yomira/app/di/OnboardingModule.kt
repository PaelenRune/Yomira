package com.yomira.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.yomira.app.onboarding.DataStoreOnboardingStore
import com.yomira.data.extension.ExtensionManager
import com.yomira.data.extension.FileRepositoryStore
import com.yomira.data.extension.InstalledExtensionProvider
import com.yomira.data.extension.KeiyoushiRepoParser
import com.yomira.data.extension.OkHttpRepositoryClient
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.OnboardingWizardManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

internal val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {
    @Provides @Singleton
    fun store(@ApplicationContext context: Context): OnboardingStore =
        DataStoreOnboardingStore(context.onboardingDataStore)

    @Provides @Singleton
    fun extensions(@ApplicationContext context: Context, client: OkHttpClient, installed: InstalledExtensionProvider): ExtensionRepository =
        ExtensionManager(
            FileRepositoryStore(context.filesDir.toPath().resolve("extensions/repositories.json")),
            OkHttpRepositoryClient(client),
            KeiyoushiRepoParser(),
            installed,
        )

    @Provides @Singleton
    fun wizard(extensions: ExtensionRepository, store: OnboardingStore): OnboardingWizardManager =
        OnboardingWizardManager(extensions, store)
}
