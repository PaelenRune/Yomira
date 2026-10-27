package com.yomira.app.di

import com.yomira.data.sync.DriveAccessTokenProvider
import com.yomira.app.settings.GoogleDriveAuthStore
import com.yomira.app.settings.DriveAccountAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** OAuth boundary: no client secret or static access token is shipped or persisted. */
@Module
@InstallIn(SingletonComponent::class)
object SyncAuthModule {
    @Provides
    fun provideDriveAccountAuth(auth: GoogleDriveAuthStore): DriveAccountAuth = auth

    @Provides
    @Singleton
    fun provideDriveAccessTokenProvider(auth: GoogleDriveAuthStore): DriveAccessTokenProvider =
        DriveAccessTokenProvider { auth.accessToken() }
}
