package com.scrollshield.reports.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scrollshield.data.db.SessionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RetentionEntryPoint {
    fun sessionDao(): SessionDao
}

class RetentionCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "scrollshield_retention_cleanup"
        const val RETENTION_DAYS = 90L
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, RetentionEntryPoint::class.java
            )
            val cutoff = System.currentTimeMillis() - RETENTION_DAYS * MS_PER_DAY
            entryPoint.sessionDao().deleteOlderThan(cutoff)
            // Note: monthly_aggregates intentionally untouched.
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }
}
