package com.yomira.app.di

import com.yomira.core.database.dao.MetadataCacheDao
import com.yomira.core.database.dao.SeriesAliasDao
import com.yomira.core.database.metadata.RoomMetadataCache
import com.yomira.core.database.metadata.RoomSeriesAliasSink
import com.yomira.core.network.AniListClient
import com.yomira.core.network.BakaUpdatesClient
import com.yomira.core.network.ComicVineClient
import com.yomira.core.network.KitsuClient
import com.yomira.core.network.MangaDexClient
import com.yomira.core.network.NetworkConfiguration
import com.yomira.core.network.OpenLibraryClient
import com.yomira.data.metadata.SeriesConnectionRepository
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
    fun provideBakaUpdatesClient(
        client: OkHttpClient,
        config: NetworkConfiguration,
    ): BakaUpdatesClient = BakaUpdatesClient(client, config = config)

    @Provides
    @Singleton
    fun provideNetworkConfiguration(): NetworkConfiguration = NetworkConfiguration()

    @Provides
    @Singleton
    fun provideComicVineClient(
        client: OkHttpClient,
        config: NetworkConfiguration,
    ): ComicVineClient = ComicVineClient(client, config = config)

    @Provides
    @Singleton
    fun provideOpenLibraryClient(
        client: OkHttpClient,
        config: NetworkConfiguration,
    ): OpenLibraryClient = OpenLibraryClient(client, config = config)

    @Provides
    @Singleton
    fun provideSeriesConnectionRepository(aniList: AniListClient): SeriesConnectionRepository =
        SeriesConnectionRepository(aniList)

    @Provides
    @Singleton
    fun provideAggregator(
        client: OkHttpClient,
        cache: MetadataCacheDao,
        aliasDao: SeriesAliasDao,
        aniList: AniListClient,
        mangaDex: MangaDexClient,
        bakaUpdates: BakaUpdatesClient,
        comicVine: ComicVineClient,
        openLibrary: OpenLibraryClient,
    ): WaterfallMetadataAggregator =
        WaterfallMetadataAggregator(
            listOf(
                bakaUpdates,
                aniList,
                mangaDex,
                KitsuClient(client),
                comicVine,
                openLibrary,
            ),
            RoomMetadataCache(cache),
            // The waterfall is the sole writer of the alias dictionary; reads
            // happen through SeriesAliasDao wherever a lookup resolves names.
            aliasSink = RoomSeriesAliasSink(aliasDao),
        )
}
