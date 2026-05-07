package com.scrollshield.di

import android.content.Context
import android.media.projection.MediaProjectionManager
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.error.ErrorRecoveryManager
import com.scrollshield.service.MediaProjectionHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaProjectionModule {

    @Provides
    @Singleton
    fun provideMediaProjectionManager(@ApplicationContext context: Context): MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Provides
    @Singleton
    fun provideScreenCaptureManager(
        @ApplicationContext context: Context,
        mediaProjectionManager: MediaProjectionManager,
        errorRecoveryManager: ErrorRecoveryManager,
        diagnosticLogger: DiagnosticLogger
    ): ScreenCaptureManager = ScreenCaptureManager(context, mediaProjectionManager, errorRecoveryManager, diagnosticLogger)

    @Provides
    @Singleton
    fun provideMediaProjectionHolder(
        @ApplicationContext context: Context,
        mediaProjectionManager: MediaProjectionManager,
        screenCaptureManager: ScreenCaptureManager,
        errorRecoveryManager: ErrorRecoveryManager
    ): MediaProjectionHolder = MediaProjectionHolder(context, mediaProjectionManager, screenCaptureManager, errorRecoveryManager)
}
