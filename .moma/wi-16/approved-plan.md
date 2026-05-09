# Approved Plan — Iteration 10 (New Run)

## Changes to `app/build.gradle.kts`

- **After line 84** (`implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")`), add:
  ```kotlin
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  ```
  This adds `EncryptedSharedPreferences` to the classpath, resolving the compile error that blocked criterion 8 in all previous iterations. Version `1.1.0-alpha06` is the latest stable-ish release that supports API 28+.

## Changes to `app/src/main/AndroidManifest.xml`

- **After the existing `<service android:name=".accessibility.ScrollShieldAccessibilityService" ...>` block (before `</application>`)**, add a new foreground service declaration:
  ```xml
  <service
      android:name=".service.ScreenCaptureService"
      android:exported="false"
      android:foregroundServiceType="mediaProjection" />
  ```
  Also add `FeedInterceptionService` declaration since it is referenced in the codebase but not yet declared:
  ```xml
  <service
      android:name=".service.FeedInterceptionService"
      android:exported="false" />
  ```
  The `foregroundServiceType="mediaProjection"` on `ScreenCaptureService` is required on API 29+ for MediaProjection foreground services and mandatory on API 34+ per Android 14 policy. `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission is already declared (line 6 of manifest).

## Changes to `app/src/main/java/com/scrollshield/classification/ScreenCaptureManager.kt`

Complete rewrite. Key decisions:

**Constructor**: Remove `@Inject` annotation (to avoid duplicate binding with `@Provides` in module). Constructor takes `@ApplicationContext context: Context` and `mediaProjectionManager: MediaProjectionManager` (both provided by Hilt module). Remove `@Singleton` from class declaration (scoping owned by module's `@Provides @Singleton`).

**Public API** (compatible with `FeedInterceptionService` and `ClassificationPipeline`):
- `fun start(mediaProjection: MediaProjection)` — sets up `ImageReader` and `VirtualDisplay`, starts `ScreenCaptureService` foreground notification, registers no callback (callback registration is `MediaProjectionHolder`'s responsibility per spec).
- `fun stop()` — tears down `VirtualDisplay` and `ImageReader`, stops `ScreenCaptureService`.
- `suspend fun captureFrame(): Bitmap?` — acquires, validates staleness, crops, returns.
- `val isAvailable: Boolean get() = mediaProjection != null && imageReader != null`

**`start(mediaProjection: MediaProjection)`** implementation:
```kotlin
fun start(mediaProjection: MediaProjection) {
    stop() // clean up any prior state
    this.mediaProjection = mediaProjection
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        displayWidth = bounds.width()
        displayHeight = bounds.height()
        val insets = wm.currentWindowMetrics.windowInsets
        statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
        navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
    } else {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        displayWidth = dm.widthPixels
        displayHeight = dm.heightPixels
        statusBarHeight = 0
        navBarHeight = 0
    }
    val reader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
    imageReader = reader
    try {
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScrollShieldCapture", displayWidth, displayHeight,
            context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )
    } catch (e: SecurityException) {
        android.util.Log.e("SCM", "createVirtualDisplay denied", e)
        reader.close()
        imageReader = null
        this.mediaProjection = null
        return
    }
    // Start foreground service for platform requirement
    val svcIntent = Intent(context, ScreenCaptureService::class.java)
        .setAction(ScreenCaptureService.ACTION_START)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(svcIntent)
    } else {
        context.startService(svcIntent)
    }
}
```

**`stop()`** implementation:
```kotlin
fun stop() {
    try { virtualDisplay?.release() } catch (_: Exception) {}
    virtualDisplay = null
    try { imageReader?.close() } catch (_: Exception) {}
    imageReader = null
    try { mediaProjection?.stop() } catch (_: Exception) {}
    mediaProjection = null
    val svcIntent = Intent(context, ScreenCaptureService::class.java)
        .setAction(ScreenCaptureService.ACTION_STOP)
    context.startService(svcIntent)
}
```

**`suspend fun captureFrame(): Bitmap?`** — `withContext(Dispatchers.IO)`:
```kotlin
suspend fun captureFrame(): Bitmap? = withContext(Dispatchers.IO) {
    val reader = imageReader ?: return@withContext null
    // First attempt
    var image = try { reader.acquireLatestImage() } catch (_: Exception) { return@withContext null }
    if (image == null) {
        // Retry once after 50ms
        delay(50)
        image = try { reader.acquireLatestImage() } catch (_: Exception) { return@withContext null }
            ?: return@withContext null
    }
    try {
        // Stale frame detection: Image.timestamp uses elapsedRealtimeNanos epoch
        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (nowNs - image.timestamp > 200_000_000L) { // 200ms in nanoseconds
            return@withContext null
        }
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * image.width
        val fullBitmap = Bitmap.createBitmap(
            image.width + rowPadding / plane.pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        try {
            fullBitmap.copyPixelsFromBuffer(plane.buffer)
            // Crop: remove status bar (top) and nav bar (bottom)
            val cropTop = statusBarHeight
            val cropHeight = image.height - statusBarHeight - navBarHeight
            if (cropHeight <= 0 || cropTop + cropHeight > fullBitmap.height) {
                return@withContext null
            }
            Bitmap.createBitmap(fullBitmap, 0, cropTop, image.width.coerceAtMost(fullBitmap.width), cropHeight)
        } finally {
            fullBitmap.recycle()
        }
    } finally {
        image.close()
    }
}
```

**Cached fields** (populated once in `start()`, not per-frame):
```kotlin
private var displayWidth = 0
private var displayHeight = 0
private var statusBarHeight = 0
private var navBarHeight = 0
private var virtualDisplay: VirtualDisplay? = null
```

**Imports**: `android.os.SystemClock`, `android.view.WindowInsets`, `android.hardware.display.VirtualDisplay`, `android.hardware.display.DisplayManager`, `com.scrollshield.service.ScreenCaptureService`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

## New File: `app/src/main/java/com/scrollshield/service/ScreenCaptureService.kt`

```kotlin
package com.scrollshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class ScreenCaptureService : Service() {

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
                startForeground(NOTIF_ID, notification)
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
```

## Changes to `app/src/main/java/com/scrollshield/service/MediaProjectionHolder.kt`

```kotlin
package com.scrollshield.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Parcel
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.scrollshield.classification.ScreenCaptureManager
import dagger.hilt.android.qualifiers.ApplicationContext

class MediaProjectionHolder(
    @ApplicationContext private val context: Context,
    private val mediaProjectionManager: MediaProjectionManager,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private var mediaProjection: MediaProjection? = null

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "scrollshield_mp_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("MPH", "EncryptedSharedPreferences init failed, falling back to plain prefs", e)
            context.getSharedPreferences("scrollshield_mp_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val revocationCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mediaProjection = null
            screenCaptureManager.stop()
        }
    }

    fun setMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection?.stop()
        val projection = try {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e("MPH", "getMediaProjection failed", e)
            return
        }
        projection.registerCallback(revocationCallback, null)
        mediaProjection = projection
        persistIntent(data, resultCode)
        screenCaptureManager.start(projection)
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    fun stop() {
        mediaProjection?.stop()
        mediaProjection = null
        screenCaptureManager.stop()
    }

    fun getStoredResultIntent(): Intent? {
        val encoded = prefs.getString("mp_result_intent", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val intent = Intent.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            intent
        } catch (e: Exception) {
            Log.e("MPH", "Failed to restore result intent", e)
            null
        }
    }

    fun getStoredResultCode(): Int =
        prefs.getInt("mp_result_code", android.app.Activity.RESULT_CANCELED)

    private fun persistIntent(data: Intent, resultCode: Int) {
        try {
            val parcel = Parcel.obtain()
            data.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            val encoded = Base64.encodeToString(bytes, Base64.DEFAULT)
            prefs.edit()
                .putString("mp_result_intent", encoded)
                .putInt("mp_result_code", resultCode)
                .apply()
        } catch (e: Exception) {
            Log.e("MPH", "Failed to persist result intent", e)
        }
    }
}
```

## Changes to `app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt`

```kotlin
package com.scrollshield.di

import android.content.Context
import android.media.projection.MediaProjectionManager
import com.scrollshield.classification.ScreenCaptureManager
import com.scrollshield.service.MediaProjectionHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaProjectionModule {

    @Provides
    @Singleton
    fun provideMediaProjectionManager(@ApplicationContext context: Context): MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Provides
    @Singleton
    fun provideScreenCaptureManager(
        @ApplicationContext context: Context,
        mediaProjectionManager: MediaProjectionManager
    ): ScreenCaptureManager = ScreenCaptureManager(context, mediaProjectionManager)

    @Provides
    @Singleton
    fun provideMediaProjectionHolder(
        @ApplicationContext context: Context,
        mediaProjectionManager: MediaProjectionManager,
        screenCaptureManager: ScreenCaptureManager
    ): MediaProjectionHolder = MediaProjectionHolder(context, mediaProjectionManager, screenCaptureManager)
}
```
