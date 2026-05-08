package com.scrollshield

import android.app.Application
import com.scrollshield.reports.work.initializeReportWorkers
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScrollShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            initializeReportWorkers(applicationContext)
        } catch (_: Exception) {
            // Worker scheduling must never crash app startup.
        }
    }
}
