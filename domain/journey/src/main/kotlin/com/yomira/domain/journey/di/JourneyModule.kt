package com.yomira.domain.journey.di

import com.yomira.domain.journey.JourneyClock
import com.yomira.domain.journey.SystemJourneyClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JourneyModule {
    @Provides
    @Singleton
    fun provideJourneyClock(): JourneyClock = SystemJourneyClock()
}
