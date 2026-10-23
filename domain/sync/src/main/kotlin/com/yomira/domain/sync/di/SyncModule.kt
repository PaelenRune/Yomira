package com.yomira.domain.sync.di

import com.yomira.data.sync.DriveAccessTokenProvider
import com.yomira.data.sync.GoogleDriveClient
import com.yomira.data.sync.SyncPayloadCodec
import com.yomira.data.sync.SyncRemoteStore
import com.yomira.domain.sync.ConflictResolutionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Wires the pure Drive transport into Hilt. The OAuth token provider is intentionally
 * bound by the app layer (see `SyncAuthModule`) because acquiring a token requires
 * Google Identity Services and the app's OAuth client configuration.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideSyncPayloadCodec(): SyncPayloadCodec = SyncPayloadCodec()

    @Provides
    @Singleton
    fun provideGoogleDriveClient(
        okHttpClient: OkHttpClient,
        tokenProvider: DriveAccessTokenProvider,
        codec: SyncPayloadCodec,
    ): GoogleDriveClient = GoogleDriveClient(
        httpClient = okHttpClient,
        tokenProvider = tokenProvider,
        codec = codec,
    )

    @Provides
    @Singleton
    fun provideSyncRemoteStore(client: GoogleDriveClient): SyncRemoteStore = client

    @Provides
    @Singleton
    fun provideConflictResolutionEngine(): ConflictResolutionEngine = ConflictResolutionEngine()
}
