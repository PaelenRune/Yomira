package com.yomira.app.di

import com.yomira.data.sync.DriveAccessTokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * OAuth boundary for Google Drive.
 *
 * Yomira never ships a client secret or a static token. Once the sign-in screen is
 * connected to Google Identity Services, replace this provider with one that returns
 * a short-lived token for the `drive.appdata` scope. Until then sync stays dormant
 * and [com.yomira.domain.sync.SyncWorker] finishes successfully instead of retrying.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncAuthModule {
    @Provides
    @Singleton
    fun provideDriveAccessTokenProvider(): DriveAccessTokenProvider =
        DriveAccessTokenProvider { null }
}
