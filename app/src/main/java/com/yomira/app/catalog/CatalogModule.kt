package com.yomira.app.catalog

import com.yomira.domain.classifier.ContentRatingClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Keeps catalogue I/O off the main thread while remaining replaceable in tests. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideContentRatingClassifier(): ContentRatingClassifier = ContentRatingClassifier()

    @Provides
    @Singleton
    fun provideCatalogAssetReader(reader: AndroidCatalogAssetReader): CatalogAssetReader = reader
}
