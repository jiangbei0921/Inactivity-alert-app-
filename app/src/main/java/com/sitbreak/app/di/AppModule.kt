package com.sitbreak.app.di

import android.content.Context
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.SettingsDataStore
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.health.StandingValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideCheckInRepository(@ApplicationContext context: Context): CheckInRepository =
        CheckInRepository(AppDatabase.getInstance(context).checkInDao())

    @Provides
    @Singleton
    fun provideStandingValidator(): StandingValidator = StandingValidator
}
