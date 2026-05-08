package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Long-lived monthly summary for trend analysis. Retained indefinitely.
 *
 * tierDistributionJson: {"tier0": Float, "tier1": Float, "tier2": Float}
 *
 * visualClassifierFeedbackJson schema:
 * {
 *   "manuallyReviewedCount": Int,   // 0 — TODO future WI post-WI-17
 *   "agreedCount": Int,             // 0 — TODO future WI
 *   "correctedCount": Int,          // 0 — TODO future WI
 *   "visualTierClassifications": Int,
 *   "visualTierShare": Double
 * }
 */
@Entity(tableName = "monthly_aggregates")
data class MonthlyAggregate(
    @PrimaryKey val id: String,
    val yearMonth: String,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val totalSessions: Int,
    val totalDurationMinutes: Float,
    val totalAdsDetected: Int,
    val totalAdsSkipped: Int,
    val averageSatisfaction: Float?,
    val perAppBreakdownJson: String,
    val topTenBrandsJson: String,
    val tierDistributionJson: String,
    val visualClassifierFeedbackJson: String,
    val computedAt: Long
)
