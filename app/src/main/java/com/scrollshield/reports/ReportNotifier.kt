package com.scrollshield.reports

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "scrollshield_reports"
        const val NOTIFICATION_ID_WEEKLY = 4201
        private const val CHANNEL_NAME = "ScrollShield reports"
        private const val CHANNEL_DESCRIPTION = "Weekly and monthly ScrollShield reports"
    }

    /** Idempotent — safe to call from any worker before posting. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    fun postWeeklyReportReady() {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ScrollShield")
            .setContentText("Your weekly ScrollShield report is ready")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_WEEKLY, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently drop.
        } catch (_: Exception) {
            // Non-fatal: scheduled work continues regardless.
        }
    }
}
