# Approved Plan — WI-13 Session Analytics & Reporting UI

Approved at iteration 2 with score 10/10.

---

## Changes to `app/src/main/java/com/scrollshield/data/model/SessionRecord.kt`

- Replace `@Entity(tableName = "sessions")` with:
  ```kotlin
  @Entity(
      tableName = "sessions",
      indices = [Index(value = ["profileId", "startTime"], name = "idx_sessions_profile_starttime")]
  )
  ```
- Add import `androidx.room.Index`. All fields and PK unchanged. Pure additive index.

## Changes to `app/src/main/java/com/scrollshield/data/db/SessionDao.kt`

Additive only — no signature changes. Append:
- `@Query("SELECT COUNT(*) FROM sessions WHERE startTime >= :since AND startTime < :until") suspend fun countInRange(since: Long, until: Long): Int`
- `@Query("SELECT * FROM sessions WHERE startTime >= :since AND startTime < :until ORDER BY startTime ASC") suspend fun getSessionsInRange(since: Long, until: Long): List<SessionRecord>`
- `@Query("SELECT * FROM sessions WHERE profileId = :profileId AND startTime >= :since AND startTime < :until ORDER BY startTime ASC") suspend fun getSessionsByProfileInRange(profileId: String, since: Long, until: Long): List<SessionRecord>`
- `@RawQuery suspend fun rawAggregate(query: SupportSQLiteQuery): List<AggregateRow>`
- Imports: `androidx.room.RawQuery`, `androidx.sqlite.db.SupportSQLiteQuery`, `com.scrollshield.reports.AggregateRow`.

## New file: `app/src/main/java/com/scrollshield/data/model/MonthlyAggregate.kt`

```kotlin
package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Long-lived monthly summary for trend analysis. Retained indefinitely
 * (NOT deleted by 90-day retention worker).
 *
 * tierDistributionJson: {"tier0": Float, "tier1": Float, "tier2": Float}
 *   percent of classified items per tier; 0..1, sums to ~1.0.
 *
 * visualClassifierFeedbackJson schema:
 * {
 *   "manuallyReviewedCount": Int,   // 0 — TODO future WI post-WI-17
 *   "agreedCount": Int,             // 0 — TODO future WI
 *   "correctedCount": Int,          // 0 — TODO future WI
 *   "visualTierClassifications": Int,   // proxy: count of Tier 1 items in month
 *   "visualTierShare": Double           // proxy: tier1 / total classified
 * }
 */
@Entity(tableName = "monthly_aggregates")
data class MonthlyAggregate(
    @PrimaryKey val id: String,            // "YYYY-MM"
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
```

## New file: `app/src/main/java/com/scrollshield/data/db/MonthlyAggregateDao.kt`

```kotlin
@Dao
interface MonthlyAggregateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(aggregate: MonthlyAggregate)
    @Query("SELECT * FROM monthly_aggregates ORDER BY periodStartMs DESC") suspend fun getAll(): List<MonthlyAggregate>
    @Query("SELECT * FROM monthly_aggregates WHERE id = :id") suspend fun getById(id: String): MonthlyAggregate?
    @Query("SELECT * FROM monthly_aggregates WHERE periodStartMs >= :since ORDER BY periodStartMs DESC") suspend fun getSince(since: Long): List<MonthlyAggregate>
}
```

## Changes to `app/src/main/java/com/scrollshield/data/db/ScrollShieldDatabase.kt`

- Update `@Database(entities = [SessionRecord::class, AdSignature::class, UserProfile::class, MonthlyAggregate::class], version = 2, autoMigrations = [])`
- Add `abstract fun monthlyAggregateDao(): MonthlyAggregateDao`
- Add companion object exposing:
  ```kotlin
  val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sessions_profile_starttime` ON `sessions` (`profileId`, `startTime`)")
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `monthly_aggregates` (" +
              "`id` TEXT NOT NULL, `yearMonth` TEXT NOT NULL, " +
              "`periodStartMs` INTEGER NOT NULL, `periodEndMs` INTEGER NOT NULL, " +
              "`totalSessions` INTEGER NOT NULL, `totalDurationMinutes` REAL NOT NULL, " +
              "`totalAdsDetected` INTEGER NOT NULL, `totalAdsSkipped` INTEGER NOT NULL, " +
              "`averageSatisfaction` REAL, " +
              "`perAppBreakdownJson` TEXT NOT NULL, `topTenBrandsJson` TEXT NOT NULL, " +
              "`tierDistributionJson` TEXT NOT NULL, `visualClassifierFeedbackJson` TEXT NOT NULL, " +
              "`computedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
      }
  }
  ```
- Imports: `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`, `com.scrollshield.data.model.MonthlyAggregate`.

## Changes to `app/src/main/java/com/scrollshield/di/DatabaseModule.kt`

- Replace `.fallbackToDestructiveMigration()` with `.addMigrations(ScrollShieldDatabase.MIGRATION_1_2)`. Preserves existing user sessions on v1→v2 upgrade.
- Keep existing corruption-recovery try/catch path intact.
- Add `@Provides fun provideMonthlyAggregateDao(database: ScrollShieldDatabase): MonthlyAggregateDao = database.monthlyAggregateDao()`.

## New file: `app/src/main/java/com/scrollshield/reports/ReportModels.kt`

Defines: `AggregateRow` (with `@ColumnInfo`), `WeeklyReport`, `TierBreakdown(tier0, tier1, tier2)`, `ChildActivityReport`, `BudgetComplianceRow`, `DashboardData`. No Room annotations except `@ColumnInfo` on `AggregateRow`.

```kotlin
data class AggregateRow(
    @ColumnInfo(name = "profileId") val profileId: String,
    @ColumnInfo(name = "totalDurationMinutes") val totalDurationMinutes: Float,
    @ColumnInfo(name = "totalAdsDetected") val totalAdsDetected: Int,
    @ColumnInfo(name = "totalAdsSkipped") val totalAdsSkipped: Int,
    @ColumnInfo(name = "totalItemsSeen") val totalItemsSeen: Int,
    @ColumnInfo(name = "sessionCount") val sessionCount: Int
)
data class WeeklyReport(
    val periodStartMs: Long, val periodEndMs: Long,
    val totalTimeByProfileMinutes: Map<String, Float>,
    val adsDetected: Int, val adsSkipped: Int,
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
    val periodStartMs: Long, val periodEndMs: Long,
    val timePerAppMinutes: Map<String, Float>,
    val adsBlocked: Int,
    val categoriesEncountered: List<String>,
    val budgetCompliance: Map<String, BudgetComplianceRow>
)
data class BudgetComplianceRow(val budgetMinutes: Int, val actualMinutes: Float, val withinBudget: Boolean)
data class DashboardData(
    val rangeDays: Int, val totalSessions: Int, val totalDurationMinutes: Float,
    val adsDetected: Int, val adsSkipped: Int,
    val adFrequencyTrend: List<Float>
)
```

## New file: `app/src/main/java/com/scrollshield/reports/ReportRepository.kt`

`@Singleton` Hilt class with `SessionDao`, `MonthlyAggregateDao`, `ProfileDao` injected.

- `loadDashboard(rangeDays = 90)` — `rawAggregate` with `SimpleSQLiteQuery` against the new index.
- `loadWeeklyReport(weekEndExclusive)` — covers `[weekEndExclusive - 7d, weekEndExclusive)`. Computes total time per profile, ads detected/skipped, top-5 brands (from `adBrands`), 7-bucket ad-to-content ratio trend, satisfaction avg (skip nulls), tier breakdown via classification mapping (tier0=OFFICIAL_AD, tier1=INFLUENCER_PROMO, tier2=remaining ad-classes). KDoc the tier mapping with TODO referencing WI-08 to persist tierCounts on SessionRecord.
- `loadChildActivityReport(weekEndExclusive)` — fetches all profiles, filters `isChildProfile == true`, `getSessionsByProfileInRange` for each child profile id over past 7 days. Aggregates time per app, adsBlocked = sum(adsSkipped), categories = distinct adCategories, budget compliance per app from profile.timeBudgets.
- `upsertMonthlyAggregate(yearMonth)` — month stats with JSON for perAppBreakdown, topTenBrands, tierDistribution, visualClassifierFeedback (per the schema in MonthlyAggregate KDoc). `org.json.JSONObject`/`JSONArray`.

## New file: `app/src/main/java/com/scrollshield/reports/ReportExporter.kt`

`object` with stable, documented field ordering:
- `weeklyJson(report)` / `weeklyCsv(report)` — JSON and CSV with documented column order: `periodStartMs,periodEndMs,profileId,totalTimeMinutes,adsDetected,adsSkipped,top5Brands,avgSatisfaction,tier0,tier1,tier2,classificationCountsJson`
- `childJson(report)` / `childCsv(report)` — header `periodStartMs,periodEndMs,app,timeMinutes,adsBlockedShare,budgetMinutes,withinBudget` + `# categories: a;b;c` comment line at top.
- `monthlyJson(aggregate)` — concatenates stored JSON sub-objects in stable key order.
- All CSV quote-escape via standard CSV quoting, documented in KDoc.

## New file: `app/src/main/java/com/scrollshield/reports/ReportNotifier.kt`

`@Singleton` `@Inject` with `@ApplicationContext context`.
- `CHANNEL_ID = "scrollshield_reports"`, `NOTIFICATION_ID_WEEKLY = 4201`.
- `ensureChannel()` — `NotificationChannel` with `IMPORTANCE_DEFAULT` on Oreo+, idempotent.
- `postWeeklyReportReady()` — title/text "Your weekly ScrollShield report is ready" (verbatim), small icon `android.R.drawable.ic_dialog_info`, autoCancel true. Notify guarded by try/catch.

## New files in `app/src/main/java/com/scrollshield/reports/work/`

### `WeeklyReportWorker.kt`
- Hilt `@EntryPoint` (mirrors SignatureSyncWorker pattern, no @HiltWorker).
- `UNIQUE_WORK_NAME = "scrollshield_weekly_report"`.
- `schedule(context)`: initial delay = `ZonedDateTime.now(ZoneId.systemDefault()).with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).toLocalDate().atStartOfDay(zone)` (always strictly future). `PeriodicWorkRequestBuilder<>(7, TimeUnit.DAYS).setInitialDelay(...)`. `enqueueUniquePeriodicWork(name, UPDATE, request)`.
- `doWork()`: ensureChannel, loadWeeklyReport(now), postWeeklyReportReady. Retry once on exception, then failure.

### `MonthlyAggregateWorker.kt`
- 1-day periodic. Gates on `LocalDate.now().dayOfMonth == 1`. Computes previous YearMonth, calls `upsertMonthlyAggregate`. KDoc explains gate strategy.

### `RetentionCleanupWorker.kt`
- 1-day periodic. `RETENTION_DAYS = 90L`. `cutoff = now - 90d`, `sessionDao.deleteOlderThan(cutoff)`. Does NOT touch monthly_aggregates. KDoc states aggregates retained indefinitely.

### `ReportWorkers.kt`
- Top-level `initializeReportWorkers(context)` calls all three `.schedule(context)`.

## New file: `app/src/main/java/com/scrollshield/ui/reports/ReportViewModel.kt`

`@HiltViewModel` `@Inject` constructor. State: `dashboard`, `weekly`, `child`, `monthlies`, `isLoading` as `StateFlow`. `init` loads dashboard + weekly + monthlies. Child on-demand via `loadChildReport()`. Six exports: `exportWeeklyJson/Csv`, `exportChildJson/Csv`, `exportMonthlyJson(yearMonth)`. `writeToDownloads` mirrors existing `AdCounterManager.exportToDownloads` (MediaStore on Q+, fallback below).

## New target files: `ReportScreen.kt`, `WeeklyReportCard.kt`, `ChildActivityReport.kt`

- `ReportScreen` — `LazyColumn` with: header, dashboard summary card, `WeeklyReportCard`, `ChildActivityReportSection`, monthly aggregates list. CircularProgressIndicator when loading.
- `WeeklyReportCard(report, onExportJson, onExportCsv)` — card with period range, total time by profile, ads detected/skipped, top 5 brands, 7-element ratio trend (Row of Boxes proportional to ratio, no chart deps), avg satisfaction, three tier rows. Two OutlinedButton exports. Empty state "No weekly report available yet".
- `ChildActivityReportSection(report, onGenerate, onExportJson, onExportCsv)` — null state Generate button (on-demand). Non-null shows time per app, ads blocked, categories chips, per-app budget compliance, two export buttons.

## Changes to `app/src/main/java/com/scrollshield/MainActivity.kt`

- Wrap onboarding-completed branch in `Scaffold` with `NavigationBar` (two `NavigationBarItem`s: Settings, Reports). Use `Icons.Default.Settings` and `Icons.Default.Assessment` — fallback to `Icons.Default.List` if not in core set. No `androidx.navigation` dep needed. `selectedTab` state controls which screen renders.

## Changes to `app/src/main/java/com/scrollshield/ScrollShieldApp.kt`

- Override `onCreate()`: after `super.onCreate()`, call `com.scrollshield.reports.work.initializeReportWorkers(applicationContext)`. Keep `@HiltAndroidApp`.

## Build verification

```
cd /home/devuser/ScrollShield2
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.
