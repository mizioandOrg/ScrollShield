package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey val id: String,
    val profileId: String,
    val app: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Float,
    val itemsSeen: Int,
    val itemsPreScanned: Int,
    val adsDetected: Int,
    val adsSkipped: Int,
    val adBrands: List<String>,
    val adCategories: List<String>,
    val estimatedRevenue: Float,
    val satisfactionRating: Int?,
    val maskWasEnabled: Boolean,
    val preScanDurationMs: Long,
    val classificationCounts: Map<Classification, Int>,
    val endedNormally: Boolean
)
