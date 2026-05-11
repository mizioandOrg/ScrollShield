package com.scrollshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.scrollshield.classification.ScreenCaptureManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ScreenCaptureService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScreenCaptureServiceEntryPoint {
        fun screenCaptureManager(): ScreenCaptureManager
    }

    companion object {
        const val ACTION_START = "com.scrollshield.action.SCREEN_CAPTURE_START"
        const val ACTION_STOP  = "com.scrollshield.action.SCREEN_CAPTURE_STOP"
        private const val CHANNEL_ID = "scrollshield_capture"
        private const val NOTIF_ID   = 9001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setContentTitle("ScrollShield visual protection active")
                    .setOngoing(true)
                    .build()
                // Must call startForeground with MEDIA_PROJECTION type before createVirtualDisplay (API 34+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
                val scm = EntryPointAccessors.fromApplication(
                    applicationContext, ScreenCaptureServiceEntryPoint::class.java
                ).screenCaptureManager()
                scm.initVirtualDisplay()
            }
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
