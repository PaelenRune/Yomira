package com.yomira.app.di

import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.network.AniListClient
import com.yomira.core.network.BakaUpdatesClient
import com.yomira.core.network.ComicVineClient
import com.yomira.core.network.KitsuClient
import com.yomira.core.network.MangaDexClient
import com.yomira.data.metadata.RoomMetadataCache
import com.yomira.data.metadata.WaterfallMetadataAggregator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object MetadataModule {
    @Provides
    @Singleton
    fun provideAniListClient(client: OkHttpClient): AniListClient = AniListClient(client)

    @Provides
    @Singleton
    fun provideMangaDexClient(client: OkHttpClient): MangaDexClient = MangaDexClient(client)

    @Provides
    @Singleton
    fun provideAggregator(
        client: OkHttpClient,
        cache: MetadataCacheDao,
        aniList: AniListClient,
        mangaDex: MangaDexClient,
    ): WaterfallMetadataAggregator =
        WaterfallMetadataAggregator(
            listOf(
                BakaUpdatesClient(client),
                aniList,
                mangaDex,
                KitsuClient(client),
                ComicVineClient(client),
            ),
            RoomMetadataCache(cache),
        )
}
