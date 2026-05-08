package com.scrollshield.reports.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrollshield.reports.ReportNotifier
import com.scrollshield.reports.ReportRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReportsEntryPoint {
    fun reportRepository(): ReportRepository
    fun reportNotifier(): ReportNotifier
}

class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "scrollshield_weekly_report"

        fun schedule(context: Context) {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val nextSundayMidnight = now
                .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .toLocalDate()
                .atStartOfDay(zone)
            val initialDelayMs = Duration.between(now, nextSundayMidnight).toMillis()
                .coerceAtLeast(60_000L)
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ReportsEntryPoint::class.java
        )
        val repo = entryPoint.reportRepository()
        val notifier = entryPoint.reportNotifier()
        return try {
            notifier.ensureChannel()
            repo.loadWeeklyReport(System.currentTimeMillis())
            notifier.postWeeklyReportReady()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }
}
