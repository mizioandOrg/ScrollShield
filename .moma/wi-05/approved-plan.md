# Approved Plan — WI-05 Feed Interception Service
# (Iteration 5 + criterion-2 fix applied by user directive)

## Fix applied beyond Iteration 5

In `onAccessibilityEvent()`, `TYPE_WINDOWS_CHANGED` must be handled BEFORE the
`if (pkg !in TARGET_PACKAGES) return` guard, because window-change events may
arrive with a non-target/system package name.

---

## File 1: `app/src/main/java/com/scrollshield/compat/AppCompatLayer.kt`

```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

abstract class AppCompatLayer {
    abstract fun extractCreator(root: AccessibilityNodeInfo): String?
    abstract fun extractCaption(root: AccessibilityNodeInfo): String?
    abstract fun extractAdLabel(root: AccessibilityNodeInfo): String?
    abstract fun extractHashtags(root: AccessibilityNodeInfo): List<String>
}
```

## File 2: `app/src/main/java/com/scrollshield/compat/TikTokCompat.kt`

Resource IDs from WI-05: title, desc, ad_label

```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class TikTokCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/title")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/desc")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/ad_label")
            .firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

## File 3: `app/src/main/java/com/scrollshield/compat/InstagramCompat.kt`

Resource IDs from WI-05: reel_viewer_title, reel_viewer_caption, sponsored_label

```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class InstagramCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_title")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_caption")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/sponsored_label")
            .firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

## File 4: `app/src/main/java/com/scrollshield/compat/YouTubeCompat.kt`

Resource IDs from WI-05: reel_channel_name, reel_multi_format_title. No ad label.

```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class YouTubeCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_channel_name")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_multi_format_title")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? = null
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

## File 5: `app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt`

Key: TYPE_WINDOWS_CHANGED handled BEFORE the pkg early-return guard.

```kotlin
package com.scrollshield.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.scrollshield.compat.AppCompatLayer
import com.scrollshield.compat.InstagramCompat
import com.scrollshield.compat.TikTokCompat
import com.scrollshield.compat.YouTubeCompat
import com.scrollshield.data.model.FeedItem
import com.scrollshield.util.FeedFingerprint
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FeedInterceptionService : AccessibilityService() {

    companion object {
        private val TARGET_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.instagram.android",
            "com.google.android.youtube"
        )
        private const val ACTION_APP_FOREGROUND   = "com.scrollshield.APP_FOREGROUND"
        private const val ACTION_APP_BACKGROUND   = "com.scrollshield.APP_BACKGROUND"
        private const val NOTIF_CHANNEL_ID        = "feed_interception"
        private const val NOTIF_FOREGROUND_ID     = 1001
        private const val NOTIF_UNAVAILABLE_ID    = 1002
        private const val MODAL_TIMEOUT_MS        = 2000L
        private const val SWIPE_FORWARD_DURATION  = 150L
        private const val SWIPE_FAST_DURATION     = 100L
        private const val SWIPE_FAST_PAUSE        = 200L
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler  = Handler(Looper.getMainLooper())

    @Volatile var activePkg: String? = null
        private set
    @Volatile var feedPosition: Int = 0
        private set
    @Volatile var isOwnGesture: Boolean = false
        private set

    private var webViewActive              = false
    private var lastValidatedHash: String? = null
    private var modalPending               = false
    private var unavailableNotifShown      = false
    private var projectionEverGranted      = false

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader?          = null

    private val compatMap: Map<String, AppCompatLayer> = mapOf(
        "com.zhiliaoapp.musically"   to TikTokCompat(),
        "com.instagram.android"      to InstagramCompat(),
        "com.google.android.youtube" to YouTubeCompat()
    )

    override fun onServiceConnected() {
        createNotificationChannel()
    }

    override fun onInterrupt() {
        isOwnGesture = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        detachMediaProjection()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // TYPE_WINDOWS_CHANGED must be handled BEFORE the package guard —
        // these events may arrive with a system/non-target package name.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handleWindowsChanged()
            return
        }

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return          // early return: non-target packages

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (activePkg == null) return            // zero-processing guard
            handleContentChange(pkg)
        }
    }

    // ---- Foreground / background tracking ----

    private fun handleWindowsChanged() {
        val targetPkg = windows
            ?.firstOrNull { w ->
                w.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                w.root?.packageName?.toString() in TARGET_PACKAGES
            }
            ?.root?.packageName?.toString()

        val old = activePkg
        if (targetPkg == old) return

        activePkg = targetPkg
        if (old != null && targetPkg == null)
            sendBroadcast(Intent(ACTION_APP_BACKGROUND).putExtra("pkg", old))
        if (targetPkg != null && old == null)
            sendBroadcast(Intent(ACTION_APP_FOREGROUND).putExtra("pkg", targetPkg))
    }

    // ---- Content extraction ----

    private fun handleContentChange(pkg: String) {
        val root = rootInActiveWindow ?: return

        // WebView detection: pause when WebView is present
        if (containsWebView(root)) {
            if (!webViewActive) webViewActive = true
            root.recycle()
            return
        }
        if (webViewActive) {
            webViewActive = false
            val newHash = fingerprintOf(root)
            if (lastValidatedHash != null && newHash != lastValidatedHash)
                sendBroadcast(Intent("com.scrollshield.SCAN_MAP_INVALID"))
            lastValidatedHash = newHash
        }

        // Modal detection (synchronous, non-blocking)
        if (!modalPending && isModalPresent(root)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            modalPending = true
            mainHandler.postDelayed({ modalPending = false }, MODAL_TIMEOUT_MS)
            root.recycle()
            return
        }
        if (modalPending) { root.recycle(); return }

        val c = compatMap[pkg] ?: run { root.recycle(); return }
        val creator  = c.extractCreator(root)  ?: ""
        val caption  = c.extractCaption(root)  ?: ""
        val hashtags = c.extractHashtags(root)
        val adLabel  = c.extractAdLabel(root)
        val region   = Rect().also { root.getBoundsInScreen(it) }
        val nodeData = collectNodes(root)
        root.recycle()

        val id      = sha256("$caption$creator$pkg$feedPosition")
        val capture = captureFrame()
        lastValidatedHash = FeedFingerprint.fingerprint(nodeData)

        val item = FeedItem(
            id                  = id,
            timestamp           = System.currentTimeMillis(),
            app                 = pkg,
            creatorName         = creator,
            captionText         = caption,
            hashtags            = hashtags,
            labelText           = adLabel,
            screenRegion        = region,
            rawNodeDump         = "",
            feedPosition        = feedPosition,
            accessibilityNodeId = null,
            detectedDurationMs  = null,
            screenCapture       = capture
        )
        sendBroadcast(Intent("com.scrollshield.FEED_ITEM").putExtra("item_id", item.id))
    }

    // ---- Modal detection ----

    private fun isModalPresent(root: AccessibilityNodeInfo): Boolean {
        val cls = root.className?.toString() ?: ""
        if (cls.contains("Dialog")) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && root.isModal) return true
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (isModalPresent(child)) { child.recycle(); return true }
            child.recycle()
        }
        return false
    }

    // ---- WebView detection ----

    private fun containsWebView(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.contains("WebView") == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsWebView(child)) { child.recycle(); return true }
            child.recycle()
        }
        return false
    }

    // ---- Fingerprint helpers ----

    private fun collectNodes(root: AccessibilityNodeInfo): List<FeedFingerprint.NodeData> {
        val list = mutableListOf<FeedFingerprint.NodeData>()
        fun walk(n: AccessibilityNodeInfo) {
            list += FeedFingerprint.NodeData(
                className          = n.className?.toString() ?: "",
                viewIdResourceName = n.viewIdResourceName ?: "",
                text               = n.text?.toString() ?: "",
                contentDescription = n.contentDescription?.toString() ?: ""
            )
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        walk(root)
        return list
    }

    private fun fingerprintOf(root: AccessibilityNodeInfo): String =
        FeedFingerprint.fingerprint(collectNodes(root))

    // ---- Screen dimensions (API-adaptive) ----

    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    // ---- Gesture dispatch ----

    private suspend fun dispatchSwipe(upward: Boolean, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val (w, h) = getScreenDimensions()
            val cx     = w / 2f
            val startY = if (upward) h * 0.75f else h * 0.25f
            val endY   = if (upward) h * 0.25f else h * 0.75f
            val path   = Path().apply { moveTo(cx, startY); lineTo(cx, endY) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            isOwnGesture = true
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    isOwnGesture = false
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(g: GestureDescription) {
                    isOwnGesture = false
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!dispatched) { isOwnGesture = false; if (cont.isActive) cont.resume(false) }
            cont.invokeOnCancellation { isOwnGesture = false }
        }

    fun scrollForward(): Job = serviceScope.launch {
        val ok = dispatchSwipe(upward = true, durationMs = SWIPE_FORWARD_DURATION)
        if (ok) {
            feedPosition++
            rootInActiveWindow?.let { r -> lastValidatedHash = fingerprintOf(r); r.recycle() }
        }
    }

    fun scrollBackward(): Job = serviceScope.launch {
        val ok = dispatchSwipe(upward = false, durationMs = SWIPE_FORWARD_DURATION)
        if (ok) {
            feedPosition = maxOf(0, feedPosition - 1)
            rootInActiveWindow?.let { r -> lastValidatedHash = fingerprintOf(r); r.recycle() }
        }
    }

    fun scrollForwardFast(n: Int): Job = serviceScope.launch {
        repeat(n) { i ->
            var ok = dispatchSwipe(upward = true, durationMs = SWIPE_FAST_DURATION)
            if (!ok) {
                ok = dispatchSwipe(upward = true, durationMs = SWIPE_FAST_DURATION)
                if (!ok) {
                    sendBroadcast(Intent("com.scrollshield.FALLBACK_LIVE_CLASSIFICATION"))
                    return@launch
                }
            }
            feedPosition++
            rootInActiveWindow?.let { r -> lastValidatedHash = fingerprintOf(r); r.recycle() }
            if (i < n - 1) delay(SWIPE_FAST_PAUSE)
        }
    }

    fun scrollBackwardFast(n: Int): Job = serviceScope.launch {
        repeat(n) { i ->
            var ok = dispatchSwipe(upward = false, durationMs = SWIPE_FAST_DURATION)
            if (!ok) {
                ok = dispatchSwipe(upward = false, durationMs = SWIPE_FAST_DURATION)
                if (!ok) {
                    sendBroadcast(Intent("com.scrollshield.FALLBACK_LIVE_CLASSIFICATION"))
                    return@launch
                }
            }
            feedPosition = maxOf(0, feedPosition - 1)
            rootInActiveWindow?.let { r -> lastValidatedHash = fingerprintOf(r); r.recycle() }
            if (i < n - 1) delay(SWIPE_FAST_PAUSE)
        }
    }

    // ---- MediaProjection ----

    fun attachMediaProjection(intent: Intent, resultCode: Int) {
        projectionEverGranted = true
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, intent)
        val (w, h) = getScreenDimensions()
        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        mediaProjection!!.createVirtualDisplay(
            "ScrollShieldCapture", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        showForegroundNotification()
    }

    fun detachMediaProjection() {
        mediaProjection?.stop(); mediaProjection = null
        imageReader?.close();    imageReader = null
        stopForegroundCompat()
    }

    private fun captureFrame(): Bitmap? {
        val reader = imageReader ?: run { ensureUnavailableNotification(); return null }
        val t0    = System.currentTimeMillis()
        val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
            ?: return null
        return try {
            val plane = image.planes[0]
            val rp    = plane.rowStride - plane.pixelStride * image.width
            val bmp   = Bitmap.createBitmap(
                image.width + rp / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            if (System.currentTimeMillis() - t0 > 15)
                android.util.Log.w("FIS", "frame capture exceeded 15ms budget")
            bmp
        } finally { image.close() }
    }

    private fun ensureUnavailableNotification() {
        if (projectionEverGranted) return   // don't fire after voluntary detach
        if (unavailableNotifShown) return
        unavailableNotifShown = true
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_UNAVAILABLE_ID,
            android.app.Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("ScrollShield visual protection unavailable")
                .setContentText("Grant screen capture for full protection.")
                .setOngoing(true).build())
    }

    private fun showForegroundNotification() {
        startForeground(NOTIF_FOREGROUND_ID,
            android.app.Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("ScrollShield active")
                .setContentText("Screen capture active — visual protection enabled.")
                .setOngoing(true).build())
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                NOTIF_CHANNEL_ID, "Feed Interception",
                android.app.NotificationManager.IMPORTANCE_LOW))
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```
