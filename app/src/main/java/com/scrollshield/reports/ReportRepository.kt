package com.scrollshield.reports

import androidx.sqlite.db.SimpleSQLiteQuery
import com.scrollshield.data.db.MonthlyAggregateDao
import com.scrollshield.data.db.ProfileDao
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.MonthlyAggregate
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.data.model.UserProfile
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val monthlyAggregateDao: MonthlyAggregateDao,
    private val profileDao: ProfileDao
) {

    /**
     * Loads dashboard summary over the last [rangeDays] days. Uses @RawQuery aggregation
     * over the (profileId, startTime) index for sub-second performance over 90 days.
     */
    suspend fun loadDashboard(rangeDays: Int = DEFAULT_DASHBOARD_RANGE_DAYS): DashboardData {
        val now = System.currentTimeMillis()
        val since = now - rangeDays.toLong() * MS_PER_DAY
        val sql = """
            SELECT
                profileId AS profileId,
                COALESCE(SUM(durationMinutes), 0) AS totalDurationMinutes,
                COALESCE(SUM(adsDetected), 0) AS totalAdsDetected,
                COALESCE(SUM(adsSkipped), 0) AS totalAdsSkipped,
                COALESCE(SUM(itemsSeen), 0) AS totalItemsSeen,
                COUNT(*) AS sessionCount
            FROM sessions
            WHERE startTime >= ? AND startTime < ?
            GROUP BY profileId
        """.trimIndent()
        val rows = sessionDao.rawAggregate(SimpleSQLiteQuery(sql, arrayOf<Any>(since, now)))
        val totalSessions = rows.sumOf { it.sessionCount }
        val totalDuration = rows.sumOf { it.totalDurationMinutes.toDouble() }.toFloat()
        val totalAdsDetected = rows.sumOf { it.totalAdsDetected }
        val totalAdsSkipped = rows.sumOf { it.totalAdsSkipped }

        // Build a per-day ad frequency trend (ads per session, fallback ads-per-day).
        val sessions = sessionDao.getSessionsInRange(since, now)
        val trend = buildAdFrequencyTrend(sessions, since, now, rangeDays)
        return DashboardData(
            rangeDays = rangeDays,
            totalSessions = totalSessions,
            totalDurationMinutes = totalDuration,
            adsDetected = totalAdsDetected,
            adsSkipped = totalAdsSkipped,
            adFrequencyTrend = trend
        )
    }

    /**
     * Builds a weekly report for the 7-day window `[weekEndExclusive - 7d, weekEndExclusive)`.
     *
     * Tier mapping (from per-session classificationCounts):
     *  - tier0 (text fast-path)  : OFFICIAL_AD
     *  - tier1 (visual)          : INFLUENCER_PROMO
     *  - tier2 (deep text/other) : remaining ad-class classifications
     *
     * TODO(WI-08): persist per-session tierCounts on SessionRecord so the breakdown
     * reflects the actual classifier tier rather than this Classification mapping.
     */
    suspend fun loadWeeklyReport(weekEndExclusive: Long): WeeklyReport {
        val periodStart = weekEndExclusive - WEEK_MS
        val sessions = sessionDao.getSessionsInRange(periodStart, weekEndExclusive)

        val totalTimeByProfile = sessions.groupBy { it.profileId }
            .mapValues { (_, list) -> list.sumOf { it.durationMinutes.toDouble() }.toFloat() }
        val adsDetected = sessions.sumOf { it.adsDetected }
        val adsSkipped = sessions.sumOf { it.adsSkipped }

        // Top 5 brands across all sessions.
        val brandCounts = HashMap<String, Int>()
        for (s in sessions) for (b in s.adBrands) {
            brandCounts[b] = (brandCounts[b] ?: 0) + 1
        }
        val topFive = brandCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        // 7-bucket ad-to-content ratio trend (one bucket per day in window).
        val trend = FloatArray(7)
        val itemsTrend = FloatArray(7)
        val adsTrend = FloatArray(7)
        for (s in sessions) {
            val dayIndex = ((s.startTime - periodStart) / MS_PER_DAY).toInt().coerceIn(0, 6)
            adsTrend[dayIndex] += s.adsDetected.toFloat()
            itemsTrend[dayIndex] += s.itemsSeen.toFloat()
        }
        for (i in 0..6) {
            val items = itemsTrend[i]
            trend[i] = if (items > 0f) adsTrend[i] / items else 0f
        }

        // Average satisfaction (skip nulls).
        val satisfactions = sessions.mapNotNull { it.satisfactionRating }
        val avgSatisfaction =
            if (satisfactions.isEmpty()) null else satisfactions.sum().toFloat() / satisfactions.size

        // Tier breakdown via Classification mapping.
        val classificationCounts = HashMap<Classification, Int>()
        for (s in sessions) for ((k, v) in s.classificationCounts) {
            classificationCounts[k] = (classificationCounts[k] ?: 0) + v
        }
        val tier0 = classificationCounts[Classification.OFFICIAL_AD] ?: 0
        val tier1 = classificationCounts[Classification.INFLUENCER_PROMO] ?: 0
        val tier2 = classificationCounts.entries
            .filter { it.key != Classification.OFFICIAL_AD && it.key != Classification.INFLUENCER_PROMO }
            .filter { it.key.isAdLike() }
            .sumOf { it.value }

        return WeeklyReport(
            periodStartMs = periodStart,
            periodEndMs = weekEndExclusive,
            totalTimeByProfileMinutes = totalTimeByProfile,
            adsDetected = adsDetected,
            adsSkipped = adsSkipped,
            topFiveBrands = topFive,
            adToContentRatioTrend = trend.toList(),
            averageSatisfaction = avgSatisfaction,
            tierBreakdown = TierBreakdown(tier0, tier1, tier2),
            classificationCounts = classificationCounts.toMap()
        )
    }

    /**
     * Builds a child activity report for the past 7 days ending at [weekEndExclusive].
     * Aggregates sessions across all profiles where [UserProfile.isChildProfile] is true.
     */
    suspend fun loadChildActivityReport(weekEndExclusive: Long): ChildActivityReport {
        val periodStart = weekEndExclusive - WEEK_MS
        val allProfiles = try { profileDao.getAllProfiles().first() } catch (_: Exception) { emptyList<UserProfile>() }
        val childProfiles = allProfiles.filter { it.isChildProfile }

        val perAppTime = HashMap<String, Float>()
        val categories = HashSet<String>()
        var adsBlocked = 0
        val perAppBudget = HashMap<String, Int>()

        for (profile in childProfiles) {
            for ((app, budget) in profile.timeBudgets) {
                perAppBudget[app] = maxOf(perAppBudget[app] ?: 0, budget)
            }
            val sessions = try {
                sessionDao.getSessionsByProfileInRange(profile.id, periodStart, weekEndExclusive)
            } catch (_: Exception) { emptyList() }
            for (s in sessions) {
                perAppTime[s.app] = (perAppTime[s.app] ?: 0f) + s.durationMinutes
                adsBlocked += s.adsSkipped
                categories.addAll(s.adCategories)
            }
        }

        val compliance = HashMap<String, BudgetComplianceRow>()
        for ((app, actual) in perAppTime) {
            val budget = perAppBudget[app] ?: perAppBudget["default"] ?: 0
            compliance[app] = BudgetComplianceRow(
                budgetMinutes = budget,
                actualMinutes = actual,
                withinBudget = budget == 0 || actual <= budget.toFloat()
            )
        }

        return ChildActivityReport(
            periodStartMs = periodStart,
            periodEndMs = weekEndExclusive,
            timePerAppMinutes = perAppTime.toMap(),
            adsBlocked = adsBlocked,
            categoriesEncountered = categories.sorted(),
            budgetCompliance = compliance.toMap()
        )
    }

    /** Lists all stored monthly aggregates (newest-first). */
    suspend fun listMonthlyAggregates(): List<MonthlyAggregate> = monthlyAggregateDao.getAll()

    /**
     * Computes and upserts a [MonthlyAggregate] for [yearMonth].
     */
    suspend fun upsertMonthlyAggregate(yearMonth: YearMonth): MonthlyAggregate {
        val zone = ZoneId.systemDefault()
        val periodStart = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val periodEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = sessionDao.getSessionsInRange(periodStart, periodEnd)

        val totalDuration = sessions.sumOf { it.durationMinutes.toDouble() }.toFloat()
        val totalAdsDetected = sessions.sumOf { it.adsDetected }
        val totalAdsSkipped = sessions.sumOf { it.adsSkipped }
        val satisfactions = sessions.mapNotNull { it.satisfactionRating }
        val avgSatisfaction =
            if (satisfactions.isEmpty()) null else satisfactions.sum().toFloat() / satisfactions.size

        // Per-app breakdown: total minutes per app
        val perApp = HashMap<String, Float>()
        for (s in sessions) perApp[s.app] = (perApp[s.app] ?: 0f) + s.durationMinutes
        val perAppJson = JSONObject().apply {
            for ((k, v) in perApp.toSortedMap()) put(k, v.toDouble())
        }

        // Top 10 brands by occurrence
        val brandCounts = HashMap<String, Int>()
        for (s in sessions) for (b in s.adBrands) brandCounts[b] = (brandCounts[b] ?: 0) + 1
        val topTenBrandsJson = JSONArray()
        brandCounts.entries.sortedByDescending { it.value }.take(10).forEach { entry ->
            topTenBrandsJson.put(JSONObject().apply {
                put("brand", entry.key)
                put("count", entry.value)
            })
        }

        // Tier distribution percentages from classificationCounts
        val classificationCounts = HashMap<Classification, Int>()
        for (s in sessions) for ((k, v) in s.classificationCounts) {
            classificationCounts[k] = (classificationCounts[k] ?: 0) + v
        }
        val tier0 = classificationCounts[Classification.OFFICIAL_AD] ?: 0
        val tier1 = classificationCounts[Classification.INFLUENCER_PROMO] ?: 0
        val tier2 = classificationCounts.entries
            .filter { it.key != Classification.OFFICIAL_AD && it.key != Classification.INFLUENCER_PROMO && it.key.isAdLike() }
            .sumOf { it.value }
        val tierTotal = (tier0 + tier1 + tier2).coerceAtLeast(1)
        val tierJson = JSONObject().apply {
            put("tier0", tier0.toFloat() / tierTotal.toFloat())
            put("tier1", tier1.toFloat() / tierTotal.toFloat())
            put("tier2", tier2.toFloat() / tierTotal.toFloat())
        }

        val visualShare = if (tierTotal > 0) tier1.toDouble() / tierTotal.toDouble() else 0.0
        val visualFeedbackJson = JSONObject().apply {
            put("manuallyReviewedCount", 0)
            put("agreedCount", 0)
            put("correctedCount", 0)
            put("visualTierClassifications", tier1)
            put("visualTierShare", visualShare)
        }

        val id = "${yearMonth.year}-${"%02d".format(yearMonth.monthValue)}"
        val aggregate = MonthlyAggregate(
            id = id,
            yearMonth = id,
            periodStartMs = periodStart,
            periodEndMs = periodEnd,
            totalSessions = sessions.size,
            totalDurationMinutes = totalDuration,
            totalAdsDetected = totalAdsDetected,
            totalAdsSkipped = totalAdsSkipped,
            averageSatisfaction = avgSatisfaction,
            perAppBreakdownJson = perAppJson.toString(),
            topTenBrandsJson = topTenBrandsJson.toString(),
            tierDistributionJson = tierJson.toString(),
            visualClassifierFeedbackJson = visualFeedbackJson.toString(),
            computedAt = System.currentTimeMillis()
        )
        monthlyAggregateDao.upsert(aggregate)
        return aggregate
    }

    private fun buildAdFrequencyTrend(
        sessions: List<SessionRecord>,
        since: Long,
        now: Long,
        rangeDays: Int
    ): List<Float> {
        val span = (now - since).coerceAtLeast(1)
        val days = rangeDays.coerceAtLeast(1)
        val perDayAds = FloatArray(days)
        val perDayItems = FloatArray(days)
        for (s in sessions) {
            val idx = (((s.startTime - since).toFloat() / span.toFloat()) * days).toInt().coerceIn(0, days - 1)
            perDayAds[idx] += s.adsDetected.toFloat()
            perDayItems[idx] += s.itemsSeen.toFloat()
        }
        return (0 until days).map { i ->
            val items = perDayItems[i]
            if (items > 0f) perDayAds[i] / items else 0f
        }
    }

    private fun Classification.isAdLike(): Boolean = when (this) {
        Classification.OFFICIAL_AD,
        Classification.INFLUENCER_PROMO,
        Classification.ENGAGEMENT_BAIT,
        Classification.OUTRAGE_TRIGGER -> true
        else -> false
    }

    @Suppress("unused")
    private fun newAggregateId(): String = UUID.randomUUID().toString()

    @Suppress("unused")
    private fun todayLocalDate(): LocalDate = LocalDate.now()

    companion object {
        const val DEFAULT_DASHBOARD_RANGE_DAYS = 90
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val WEEK_MS = 7L * MS_PER_DAY
    }
}
