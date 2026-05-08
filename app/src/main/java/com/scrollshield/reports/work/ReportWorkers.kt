package com.scrollshield.reports.work

import android.content.Context

/**
 * Registers all report-related WorkManager workers.
 * Call from [com.scrollshield.ScrollShieldApp.onCreate].
 */
fun initializeReportWorkers(context: Context) {
    WeeklyReportWorker.schedule(context)
    MonthlyAggregateWorker.schedule(context)
    RetentionCleanupWorker.schedule(context)
}
