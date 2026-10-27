package com.yomira.app.di

import com.yomira.domain.extension.SmartSourceMatcher
import com.yomira.domain.extension.ExtensionRepository
import com.yomira.domain.extension.OnboardingStore
import com.yomira.domain.extension.SourceBridge
import com.yomira.domain.extension.SourceDiscovery
import com.yomira.app.catalog.MangaDexSourceBridge
import com.yomira.app.catalog.RepositorySourceDiscovery
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Production matching is always available through a native API fallback, without executing APKs. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MatcherModule {
    @BindsOptionalOf abstract fun matcher(): SmartSourceMatcher

    companion object {
        @Provides @Singleton
        fun bridge(client: OkHttpClient, extensions: ExtensionRepository, onboarding: OnboardingStore): SourceBridge =
            MangaDexSourceBridge(client, extensions, onboarding)

        @Provides @Singleton
        fun discovery(bridge: SourceBridge): SourceDiscovery = RepositorySourceDiscovery(bridge)

        @Provides @Singleton
        fun provideMatcher(extensions: ExtensionRepository, bridge: SourceBridge, discovery: SourceDiscovery): SmartSourceMatcher =
            SmartSourceMatcher(extensions, bridge, discovery)
    }
}
