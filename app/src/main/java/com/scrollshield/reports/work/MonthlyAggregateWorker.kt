package com.scrollshield.reports.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class MonthlyAggregateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "scrollshield_monthly_aggregate"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyAggregateWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        // Gate so the aggregate only runs once per month, on the 1st.
        if (LocalDate.now().dayOfMonth != 1) return Result.success()

        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, ReportsEntryPoint::class.java
            )
            val repo = entryPoint.reportRepository()
            val previousMonth = YearMonth.now().minusMonths(1)
            repo.upsertMonthlyAggregate(previousMonth)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }
}
