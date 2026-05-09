# Team Log — WI-16 Screen Capture Infrastructure

## Planner — Iteration 1

Key decisions: ScreenCaptureManager rewritten with start/stop/captureFrame (suspend), SystemClock.elapsedRealtimeNanos() for stale check, NotificationManager.notify() for notification, plain SharedPreferences with TODO, MediaProjectionHolder created, MediaProjectionModule updated with comment.

## Reviewer — Iteration 1

Passed: 1-5, 7, 9, 10.
Failed: criterion 6 (NotificationManager.notify() doesn't satisfy foreground service requirement), criterion 8 (no EncryptedSharedPreferences).

Score: 8/10

## Planner — Iteration 2

Key changes: Removed notification code entirely (delegating to FeedInterceptionService). Added androidx.security.crypto EncryptedSharedPreferences with try/catch fallback. Stale check still used SystemClock.elapsedRealtimeNanos() (regression).

## Reviewer — Iteration 2

Passed: 3-5, 7, 9, 10.
Failed: criterion 1+2 (SystemClock vs System.nanoTime() clock-source regression), criterion 6 (no notification at all), criterion 8 (compile error: androidx.security.crypto not on classpath).

Score: 6/10

## Planner — Iteration 3

Key changes: Fixed System.nanoTime() stale check, restored NotificationManager.notify() using "feed_interception" channel (ID 1003), replaced androidx.security.crypto with Android Keystore AES-256-GCM (no extra dependency), Provider<ScreenCaptureManager> in MediaProjectionHolder breaks circular dep.

## Reviewer — Iteration 3

Passed: 1-5, 7, 9, 10.
Failed: criterion 6 (NotificationManager.notify() not startForeground()), criterion 8 (Keystore AES-GCM is not EncryptedSharedPreferences per literal requirement).

Score: 8/10

## Planner — Iteration 4

Key changes: Simplified ScreenCaptureManager constructor (removed MediaProjectionHolder dep), changed captureFrame() to regular fun with Thread.sleep(50), new notification channel "scrollshield_capture" ID 9001, MediaProjectionModule replaced provideMediaProjectionManager with pass-through @Provides for both classes.

## Reviewer — Iteration 4

Passed: 1-5, 9, 10.
Failed: criterion 6 (same — NotificationManager.notify() not startForeground()), criterion 7 (new — fullBitmap not recycled on exception in processImage()), criterion 8 (same — Keystore AES-GCM not EncryptedSharedPreferences).

Score: 7/10

## Planner — Iteration 5

Key changes: Fixed criterion 7 — fullBitmap wrapped in try/finally so recycle() is called unconditionally. ScreenCaptureManager constructor simplified to @ApplicationContext only (no MediaProjectionManager). Stale check uses SystemClock.elapsedRealtimeNanos() (note: regression from iter 3 which used System.nanoTime()). MediaProjectionHolder injects ScreenCaptureManager directly (no Provider<> needed since there's no circular dep). MediaProjectionModule provides both singletons as pass-through @Provides (same pattern as iter 4). provideMediaProjectionManager removed from module. NotificationManager.notify() best-effort for criterion 6. Android Keystore AES-256-GCM best-effort for criterion 8.

## Planner — Iteration 6

Key changes: suspend fun captureFrame() with withContext(Dispatchers.IO) and delay(50) for retry (not Thread.sleep). cacheDimensionsAndInsets() called once in start() — dimensions/insets cached in fields, not recomputed per frame. ScreenCaptureManager registers its own revocationCallback in start(). MediaProjectionModule reverted to provideMediaProjectionManager only (no pass-through @Provides for @Inject singletons). fullBitmap recycled in finally block. MediaProjectionHolder injects ScreenCaptureManager directly (no circular dep since SCM only takes Context). Uses Parcel-based intent serialization in MediaProjectionHolder. SystemClock.elapsedRealtimeNanos() for stale check. Best-effort NotificationManager.notify() for criterion 6. Android Keystore AES-256-GCM for criterion 8.

## Planner — Iteration 7

Key changes: val isAvailable property (get() = mediaProjection != null && imageReader != null) — no longer a function. Removed revocationCallback from ScreenCaptureManager entirely — only MediaProjectionHolder registers the callback. suspend fun captureFrame() with delay(50) retry. cacheDimensionsAndInsets() called once in start(). fullBitmap recycled explicitly in both success and catch paths (nested try/catch pattern). MediaProjectionModule unchanged (provideMediaProjectionManager only). System.nanoTime() for stale check. Best-effort notification for criterion 6. Android Keystore AES-256-GCM for criterion 8.

## Planner — Iteration 8

Key changes: Removed @Singleton from ScreenCaptureManager and MediaProjectionHolder class declarations. Added explicit @Provides @Singleton for both in MediaProjectionModule (avoids duplicate binding by having the module own scoping). provideScreenCaptureManager constructs with context only; provideMediaProjectionHolder constructs with context, MPM, and SCM. val isAvailable property kept. No revocationCallback in SCM. suspend captureFrame() with delay(50) retry. cacheDisplayInfo() cached in start(). fullBitmap recycled in nested finally. image.close() in outer finally. System.nanoTime() stale check. Best-effort notification for criterion 6. Android Keystore AES-256-GCM for criterion 8.

## Planner — Iteration 9

Single change from iteration 8: stale check changed from System.nanoTime() to SystemClock.elapsedRealtimeNanos(). Added import android.os.SystemClock. All other aspects of iteration 8 preserved: @Singleton only via @Provides in module, val isAvailable property, suspend captureFrame with delay retry, cacheDisplayInfo() cached in start(), fullBitmap+image both in finally blocks, SecurityException caught, revocationCallback only in MediaProjectionHolder, best-effort notification (criterion 6), Android Keystore AES-256-GCM (criterion 8).

## Reviewer — Iteration 9 (FINAL)

Passed: 2, 3, 4, 5, 7.
Failed: criterion 1 (clock source flip — reviewer now says Image.timestamp uses System.nanoTime() not elapsedRealtimeNanos — contradicts reviewer 8), criterion 6 (constraint conflict — nm.notify() not startForeground()), criterion 8 (constraint conflict — no EncryptedSharedPreferences), criterion 9/10 (@Inject constructor + @Provides creates duplicate binding error; fix: remove @Inject from SCM/MPH constructors).

Score: 4/10

## Reviewer — Iteration 8

Passed: 2, 3, 4, 5, 7, 9, 10.
Failed: criterion 1 (System.nanoTime() wrong clock for Image.timestamp — reviewer says Image.timestamp uses elapsedRealtimeNanos, fix to SystemClock.elapsedRealtimeNanos()), criterion 6 (constraint conflict — plain nm.notify() not startForeground()), criterion 8 (constraint conflict — no EncryptedSharedPreferences).

Score: 7/10

## Reviewer — Iteration 7

Passed: 1, 2, 3, 4, 5, 7, 9.
Failed: criterion 6 (NotificationManager.notify() not startForeground() — constraint conflict), criterion 8 (Keystore AES-GCM not EncryptedSharedPreferences — constraint conflict), criterion 10 (module not expanded — spec says "expand existing stub" but module left unchanged; neither SCM nor MPH explicitly wired in MediaProjectionModule).

Score: 7/10

## Reviewer — Iteration 6

Passed: 1, 2, 3, 4 (SecurityException), 7, 9 (API level targeting), 10.
Failed: criterion 5 (double MediaProjection.Callback registration in both SCM and MPH — should only be in MPH per spec), criterion 6 (NotificationManager.notify() not startForeground() — same constraint conflict), criterion 8 (Keystore AES-GCM not EncryptedSharedPreferences — same constraint conflict), criterion 9 (isAvailable changed from property val to fun — breaking API change for callers using property syntax).

Score: 4/10

## Reviewer — Iteration 5

Passed: 2, 7.
Failed: criterion 1 (displayMetrics/insets recomputed per frame — 15ms budget risk; Thread.sleep on potential main thread), criterion 3 (Thread.sleep blocks calling thread; captureFrame changed from suspend to regular fun breaks API contract), criterion 4 (minor: silent failure path if createVirtualDisplay fails silently on some OEMs), criterion 5 (ScreenCaptureManager.start() doesn't register its own Callback; FeedInterceptionService calls start() directly bypassing MediaProjectionHolder), criterion 6 (NotificationManager.notify() not startForeground() — same constraint conflict), criterion 8 (Keystore AES-GCM not EncryptedSharedPreferences — same constraint conflict), criterion 9 (removing suspend from captureFrame() breaks API for ClassificationPipeline callers), criterion 10 (explicit @Provides pass-through for @Inject singletons risks duplicate binding error).

Score: 2/10

## Constraint Conflicts Documented

### Criterion 6
Review criterion: "Foreground service notification is shown while screen capture is active and removed when stopped."

AndroidManifest.xml (verified) registers only ScrollShieldAccessibilityService. Neither FeedInterceptionService nor any service with foregroundServiceType="mediaProjection" is declared. ScreenCaptureManager and MediaProjectionHolder are Hilt singletons — they cannot call startForeground(). Modifying the manifest is outside the three permitted target files.

Best-effort implementation: NotificationManager.notify() (ongoing, low priority) on start(); NotificationManager.cancel() on stop(). This satisfies the criterion text ("notification is shown while active and removed when stopped") but does not satisfy the Android platform requirement for startForeground(). The manifest gap must be resolved in a future WI.

### Criterion 8
Review criterion: "MediaProjectionHolder persists the result intent to EncryptedSharedPreferences and can recover it across app restarts."

androidx.security:security-crypto is absent from app/build.gradle.kts and is not a transitive dependency of any library on the classpath. Importing androidx.security.crypto.EncryptedSharedPreferences causes a compile error. Adding the dependency requires modifying build.gradle.kts, which is outside the three permitted target files.

Best-effort implementation: Android Keystore AES-256-GCM (platform API, available API 23+, hardware-backed). Data at rest is encrypted and recoverable across app restarts. Functionally equivalent to EncryptedSharedPreferences. TODO comment instructs future maintainer to replace with EncryptedSharedPreferences once the dependency is added to build.gradle.kts.
