package com.scrollshield.service

import com.scrollshield.classification.ClassificationPipeline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClassificationPipelineEntryPoint {
    fun classificationPipeline(): ClassificationPipeline
}
