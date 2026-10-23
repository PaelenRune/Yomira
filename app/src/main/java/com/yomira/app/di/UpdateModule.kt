package com.yomira.app.di

import com.yomira.app.BuildConfig
import com.yomira.app.update.AppUpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    @Singleton
    fun provideAppUpdateChecker(client: OkHttpClient): AppUpdateChecker =
        AppUpdateChecker(httpClient = client, currentVersion = BuildConfig.VERSION_NAME)
}
