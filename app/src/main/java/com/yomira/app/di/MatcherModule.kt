package com.yomira.app.di

import com.yomira.domain.extension.SmartSourceMatcher
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The sandbox host supplies this binding when its runtime is available. Never execute APKs in UI. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MatcherModule {
    @BindsOptionalOf abstract fun matcher(): SmartSourceMatcher
}
