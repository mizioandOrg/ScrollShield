package com.scrollshield.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePlaceholderDatabase(@ApplicationContext context: Context): Any {
        // TODO: Replace with Room database instance in WI-02
        return context.applicationContext
    }
}
