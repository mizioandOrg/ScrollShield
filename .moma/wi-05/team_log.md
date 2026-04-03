# WI-05 Team Log — Feed Interception Service

## Final Score: 8/10 (max iterations reached — not approved)

---

## Iteration 1 — Score 4/10

**Issues found:**
- criterion 2: `activePkg` never cleared — non-target packages filtered before `handleWindowStateChanged`, so service never deactivated
- criterion 5: No item-skip on modal timeout; dead-code path
- criterion 7: WebView close reset `feedPosition` to 0 too aggressively
- criterion 9: Notification spam — `showProjectionUnavailableNotification()` called on every content-change event
- criterion 10: `node.isModal` used without API 31 guard; unused `CountDownLatch`/`TimeUnit` imports

---

## Iteration 2 — Score 5/10

**Fixes applied:**
- criterion 2: Added `TYPE_WINDOWS_CHANGED` handler that iterates `windows` and sets/clears `activePkg`
- criterion 5: `dismissModalIfPresent()` returns `Boolean`; fast-scan loop uses `return@repeat` on `false` to skip item
- criterion 7: WebView close broadcasts `ACTION_SCAN_MAP_INVALID` on hash mismatch; `feedPosition` left unchanged
- criterion 9: `projectionUnavailableNotified` flag prevents repeated notifications
- criterion 10: `isModal` guarded with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; unused imports removed

**Remaining issues:**
- criterion 3 & 8: Compat files not shown — "same as Iteration 1" (unverifiable)
- criterion 6: `FeedFingerprint` stored but `feedPosition` not verified post-scroll
- criterion 10: Deprecated `Display.getRealMetrics()` without API fallback

---

## Iteration 3 — Score 5/10

**Fixes applied:**
- criterion 3 & 8: All four compat files shown in full with correct WI-05 resource IDs; YouTube `extractAdLabel()` returns null
- criterion 5: `dismissModalIfPresent` polling loop uses `continue` on null root (not `break`)
- criterion 6: `snapshotFingerprintBefore()` / `verifyPositionAfterScroll()` added
- criterion 10: `getScreenDimensions()` branches on API 30+ (`currentWindowMetrics.bounds`) vs deprecated `getRealMetrics()`

**Remaining issues:**
- criterion 5: `dismissModalIfPresent()` is `suspend` but never called from `handleContentChange()` (non-suspend) — dead code
- criterion 6: `verifyPositionAfterScroll()` not called from `scrollForward()`/`scrollBackward()`; `lastValidatedHash` not updated after scroll
- criterion 9: `ensureUnavailableNotification()` fires after `detachMediaProjection()` (confusing "never granted" with "voluntarily detached")
- criterion 10: `STOP_FOREGROUND_REMOVE` is API 33+ (compile error at minSdk 28); `CountDownLatch.await()` on main thread causes deadlock

---

## Iteration 4 — Score 8/10

**Fixes applied:**
- criterion 5: Modal detection made synchronous (`isModalPresent()` called inline in `handleContentChange()`); `modalPending` flag with `postDelayed` enforces 2s timeout; no coroutines in modal path
- criterion 6: All four gesture methods update `lastValidatedHash` via `fingerprintOf()` after each successful scroll; `feedPosition` incremented only on `ok == true`
- criterion 9: `projectionEverGranted` flag — `ensureUnavailableNotification()` returns early if projection was ever granted (prevents false-positive after voluntary `detachMediaProjection()`)
- criterion 10: `stopForegroundCompat()` helper guards `STOP_FOREGROUND_REMOVE` with API 33 check; `dispatchSwipe` converted to `suspend fun` using `suspendCancellableCoroutine` (eliminates `CountDownLatch` deadlock); all gesture methods return `Job` (launch in `serviceScope`)

**Remaining issues:**
- criterion 3: Compat files regressed to wrong resource IDs (`comments_author_text_view`, `caption_text_view`, `iv_ad_logo` for TikTok; `row_header_textview`, `inline_caption` for Instagram; `channel_name`, `title` for YouTube) — deviates from WI-05 spec
- criterion 10: Missing `import android.view.accessibility.AccessibilityWindowInfo` — compile error in `handleWindowsChanged()`

---

## Iteration 5 — Score 8/10 (final)

**Fixes applied:**
- criterion 3: All three compat files corrected to exact WI-05 resource IDs:
  - TikTok: `com.zhiliaoapp.musically:id/title`, `com.zhiliaoapp.musically:id/desc`, `com.zhiliaoapp.musically:id/ad_label`
  - Instagram: `com.instagram.android:id/reel_viewer_title`, `com.instagram.android:id/reel_viewer_caption`, `com.instagram.android:id/sponsored_label`
  - YouTube: `com.google.android.youtube:id/reel_channel_name`, `com.google.android.youtube:id/reel_multi_format_title`; `extractAdLabel()` returns `null`
- criterion 10: Added `import android.view.accessibility.AccessibilityWindowInfo`

**Remaining issues (unfixed at max iterations):**
- criterion 2: `onAccessibilityEvent()` early-return `if (pkg !in TARGET_PACKAGES) return` gates ALL event types including `TYPE_WINDOWS_CHANGED`. Window-change events may arrive with a non-target package name (system, launcher), causing the `return` to fire before `handleWindowsChanged()` is reached. `activePkg` is never set, `APP_FOREGROUND`/`APP_BACKGROUND` never broadcast.
  - **Fix required**: Move `handleWindowsChanged()` call BEFORE the `if (pkg !in TARGET_PACKAGES) return` guard. Example:
    ```kotlin
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handleWindowsChanged()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TARGET_PACKAGES) return
        // ...
    }
    ```

---

## Approved Plan: NOT APPROVED

The loop reached max iterations (5) with a final score of 8/10. The two-line fix above (moving `handleWindowsChanged()` before the package guard) would likely bring the plan to 10/10.

## Final File Plan (Iteration 5 — complete, compile-ready except criterion-2 runtime bug)

### `AppCompatLayer.kt`
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

### `TikTokCompat.kt`
```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class TikTokCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/title").firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/desc").firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/ad_label").firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

### `InstagramCompat.kt`
```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class InstagramCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_title").firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_caption").firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/sponsored_label").firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

### `YouTubeCompat.kt`
```kotlin
package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class YouTubeCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_channel_name").firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_multi_format_title").firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? = null
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
```

### `FeedInterceptionService.kt`
See Iteration 5 plan above — full file with `import android.view.accessibility.AccessibilityWindowInfo` present.
The only runtime fix needed: move `TYPE_WINDOWS_CHANGED` handling before the `pkg !in TARGET_PACKAGES` early-return.
