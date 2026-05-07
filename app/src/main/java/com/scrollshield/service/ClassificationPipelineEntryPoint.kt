package com.scrollshield.service

import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.error.ErrorRecoveryManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClassificationPipelineEntryPoint {
    fun classificationPipeline(): ClassificationPipeline
    fun errorRecoveryManager(): ErrorRecoveryManager
    fun diagnosticLogger(): DiagnosticLogger
}
