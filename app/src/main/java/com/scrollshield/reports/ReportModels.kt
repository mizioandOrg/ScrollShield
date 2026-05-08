package com.scrollshield.reports

import androidx.room.ColumnInfo
import com.scrollshield.data.model.Classification

/**
 * Aggregate row produced by the @RawQuery aggregation in [com.scrollshield.data.db.SessionDao].
 *
 * Columns are mapped by name; the SQL must use `AS` aliases that match these column names.
 */
data class AggregateRow(
    @ColumnInfo(name = "profileId") val profileId: String,
    @ColumnInfo(name = "totalDurationMinutes") val totalDurationMinutes: Float,
    @ColumnInfo(name = "totalAdsDetected") val totalAdsDetected: Int,
    @ColumnInfo(name = "totalAdsSkipped") val totalAdsSkipped: Int,
    @ColumnInfo(name = "totalItemsSeen") val totalItemsSeen: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int
)

data class WeeklyReport(
    val periodStartMs: Long,
    val periodEndMs: Long,
    val totalTimeByProfileMinutes: Map<String, Float>,
    val adsDetected: Int,
    val adsSkipped: Int,
    val topFiveBrands: List<Pair<String, Int>>,
    val adToContentRatioTrend: List<Float>,
    val averageSatisfaction: Float?,
    val tierBreakdown: TierBreakdown,
    val classificationCounts: Map<Classification, Int>
)

data class TierBreakdown(val tier0: Int, val tier1: Int, val tier2: Int) {
    val total: Int get() = tier0 + tier1 + tier2
}

data class ChildActivityReport(
    val periodStartMs: Long,
    val periodEndMs: Long,
    val timePerAppMinutes: Map<String, Float>,
    val adsBlocked: Int,
    val categoriesEncountered: List<String>,
    val budgetCompliance: Map<String, BudgetComplianceRow>
)

data class BudgetComplianceRow(
    val budgetMinutes: Int,
    val actualMinutes: Float,
    val withinBudget: Boolean
)

data class DashboardData(
    val rangeDays: Int,
    val totalSessions: Int,
    val totalDurationMinutes: Float,
    val adsDetected: Int,
    val adsSkipped: Int,
    val adFrequencyTrend: List<Float>
)
