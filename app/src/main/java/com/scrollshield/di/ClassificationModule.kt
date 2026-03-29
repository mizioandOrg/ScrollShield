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
object ClassificationModule {

    @Provides
    @Singleton
    fun providePlaceholderClassifier(@ApplicationContext context: Context): Any {
        // TODO: Replace with TFLite interpreter in WI-03
        return context.applicationContext
    }
}
