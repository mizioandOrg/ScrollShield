# ScrollShield — Technical Implementation Spec

## Project

- **Name**: ScrollShield
- **What**: Android app that intercepts doomscrolling feeds, detects ads, and gives users visibility and control over algorithmic manipulation through two features: an ad counter and a transparent content mask that pre-scans the feed and auto-skips unwanted items before the user ever sees them.
- **Platform**: Android (API 28+), no root required
- **Stack**: Kotlin, Jetpack Compose, TensorFlow Lite, Room, Hilt
- **Output**: Single installable APK

---

## Architecture

```
┌──────────────────────────────────────────────┐
│              User-Facing Features             │
│                                               │
│   ┌─────────────────┐  ┌──────────────────┐  │
│   │   Ad Counter    │  │   Scroll Mask    │  │
│   │   (overlay)     │  │   (interceptor)  │  │
│   └────────┬────────┘  └────────┬─────────┘  │
│            │                    │             │
├────────────┴────────────────────┴─────────────┤
│            Shared Infrastructure              │
│                                               │
│   ┌──────────────────────────────────────┐    │
│   │     Classification Pipeline          │    │
│   │     (Signature → Label → ML)         │    │
│   └──────────────────┬───────────────────┘    │
│   ┌──────────────────┴───────────────────┐    │
│   │     Feed Interception Service        │    │
│   │     (Accessibility Service)          │    │
│   └──────────────────────────────────────┘    │
│   ┌──────────────────────────────────────┐    │
│   │     On-Device Data Stores            │    │
│   │     (Profile, Sessions, Signatures)  │    │
│   └──────────────────────────────────────┘    │
│   ┌──────────────────────────────────────┐    │
│   │     Signature Sync (optional)        │    │
│   └──────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
```

### Core Design Principle

The user never leaves the native app. TikTok renders normally — full video, audio, native UI. ScrollShield is an invisible agent that pre-scans the feed ahead of the user and auto-skips unwanted content before the user reaches it. The user experiences a clean feed from the very first swipe.

### Key Insight: Pre-Scan Buffer

TikTok, Instagram Reels, and YouTube Shorts all maintain a back-stack — you can scroll forward and backward through previously loaded videos. ScrollShield exploits this by pre-scrolling the feed at session start, classifying every item, then scrolling back to the beginning. When the user starts swiping, ScrollShield already knows what's ahead and can skip flagged items with zero latency — the decision was made seconds ago.

This eliminates the timing race between classification and video rendering. There is no subliminal flash of ad content. No racing the renderer. The only cost is a few seconds of loading time at session start, masked by a branded animation.

### Constraints

- All classification and preference logic runs on-device. No user content leaves the phone.
- No root or jailbreak required.
- Target latency for classification pipeline: < 60ms per item.
- Must function fully offline (signature sync is optional enrichment).
- The user always sees native app video. ScrollShield never rebuilds the feed.

### How the Two Features Relate

- The **Ad Counter** is passive — observes and counts. Never modifies the experience.
- The **Scroll Mask** is active — pre-scans and auto-skips unwanted content.
- Both consume events from the same classification pipeline.
- They run independently or together. Counter is on by default. Mask is opt-in.
- When both are active, the counter shows all ads the platform *attempted* to serve, including those the mask skipped.

### Profile Switching (Parent → Child)

- **Default profile**: device owner's preferences.
- **Child profile**: restrictive config — tighter blocked categories, lower time budget, mask always on and not dismissable without parent PIN.
- Parent toggles to child profile before handing the phone over, or configures auto-activation via schedule.

---

## Data Models

### FeedItem

```kotlin
data class FeedItem(
    val id: String,                // SHA-256 of concatenated text + creator
    val timestamp: Long,           // Unix epoch ms
    val app: String,               // Package name of source app
    val creatorName: String,
    val captionText: String,
    val hashtags: List<String>,
    val labelText: String?,        // "Sponsored" / "Ad" label if found
    val screenRegion: Rect,
    val rawNodeDump: String,
    val feedPosition: Int          // Position in the feed back-stack (0 = first loaded)
)
```

### ClassifiedItem

```kotlin
data class ClassifiedItem(
    val feedItem: FeedItem,
    val classification: Classification,
    val confidence: Float,         // 0.0 to 1.0
    val topicVector: FloatArray,   // 20-dimensional
    val tier: Int,                 // Which tier classified it (1, 2, or 3)
    val latencyMs: Long,
    val skipDecision: SkipDecision // Pre-computed skip/show decision
)

enum class Classification {
    ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO,
    ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL
}

enum class SkipDecision {
    SHOW,           // Let the user see this item
    SKIP_AD,        // Auto-skip: official ad or influencer promo
    SKIP_BLOCKED,   // Auto-skip: blocked category
    SKIP_CHILD,     // Auto-skip: child-unsafe content
    SHOW_LOW_CONF   // Low confidence — fail open, let through
}
```

### UserProfile

```kotlin
data class UserProfile(
    val id: String,
    val name: String,
    val isChildProfile: Boolean,
    val interestVector: FloatArray,        // 20-dimensional
    val blockedCategories: Set<String>,
    val blockedClassifications: Set<Classification>,
    val timeBudgets: Map<String, Int>,     // Package name → minutes
    val maskEnabled: Boolean,
    val counterEnabled: Boolean,
    val maskDismissable: Boolean,          // false for child profiles
    val pinProtected: Boolean,
    val satisfactionHistory: List<Float>,
    val scoringWeights: ScoringWeights
)

data class ScoringWeights(
    val interest: Float = 0.35f,
    val wellbeing: Float = 0.25f,
    val novelty: Float = 0.15f,
    val manipulation: Float = 0.25f
)
```

### SessionRecord

```kotlin
data class SessionRecord(
    val id: String,
    val profileId: String,
    val app: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Float,
    val itemsSeen: Int,
    val itemsPreScanned: Int,              // Total items scanned during pre-scan
    val adsDetected: Int,
    val adsSkipped: Int,
    val adBrands: List<String>,
    val adCategories: List<String>,
    val estimatedRevenue: Float,
    val satisfactionRating: Int?,
    val maskWasEnabled: Boolean,
    val preScanDurationMs: Long             // How long the initial pre-scan took
)
```

### AdSignature

```kotlin
@Entity(tableName = "ad_signatures")
data class AdSignature(
    @PrimaryKey val id: String,
    val advertiser: String,
    val category: String,
    val captionHash: String,
    val firstSeen: Long,
    val expires: Long,
    val source: String
)
```

### ScanMap

The pre-scan buffer's internal state. Not persisted — lives in memory for the duration of a session.

```kotlin
data class ScanMap(
    val items: MutableList<ClassifiedItem>,   // Ordered list of all pre-scanned items
    val scanHead: Int,                        // How far ahead the pre-scan has reached
    val userHead: Int,                        // Where the user currently is in the feed
    val skipIndices: Set<Int>                 // Feed positions flagged for auto-skip
)
```

---

## Module 1: Feed Interception Service

**What**: Android AccessibilityService that monitors target apps, extracts feed content, and provides gesture dispatch for pre-scanning and auto-skipping.

**Target packages**: `com.zhiliaoapp.musically` (TikTok), `com.instagram.android`, `com.google.android.youtube`

### Core Capabilities

1. **Content extraction**: On content change events, traverse accessibility tree to extract text, creator names, hashtags, ad labels.
2. **Lifecycle events**: Emit `APP_FOREGROUND` and `APP_BACKGROUND` when target apps enter/leave foreground.
3. **Gesture dispatch**: Expose methods for programmatic feed navigation:
   - `scrollForward()`: Dispatch swipe-up gesture to advance to next video.
   - `scrollBackward()`: Dispatch swipe-down gesture to return to previous video.
   - `scrollForwardFast(n)`: Advance n items in rapid succession (for pre-scanning).
   - `scrollBackwardFast(n)`: Return n items (to reset to start after pre-scan).
4. **Position tracking**: Track the user's current position in the feed back-stack.

### Gesture Dispatch Details

- Use `AccessibilityService.dispatchGesture()` with `GestureDescription`.
- Swipe-up path: start at (screenWidth/2, screenHeight*0.75), end at (screenWidth/2, screenHeight*0.25).
- Duration per gesture: 100ms for pre-scan mode (fast, user isn't watching), 150ms for live skip mode.
- `scrollForwardFast(n)`: chain n swipe gestures with 200ms pause between each to allow the app to load the next item and the accessibility tree to update.

### Fallback Strategy

If accessibility tree is too shallow for content extraction:

1. Screen-capture OCR via `MediaProjection` API (one-time user permission).
2. If OCR insufficient: degrade to label-only detection (Tier 2) — still catches most official ads.

**Acceptance criteria**:

- Activates/deactivates on target app foreground/background.
- Extracts creator name, caption, hashtags from TikTok (or falls back gracefully).
- Detects "Sponsored" label with > 95% recall.
- `scrollForward()` reliably advances TikTok to the next video.
- `scrollBackward()` reliably returns to the previous video.
- `scrollForwardFast(10)` advances 10 items in < 4 seconds.
- `scrollBackwardFast(10)` returns 10 items in < 4 seconds.
- Position tracking is accurate after forward/backward navigation.
- No ANR under rapid gesture dispatch.
- Battery impact: < 3% additional drain per hour.

---

## Module 2: Classification Pipeline

**What**: Three-tier pipeline that classifies each FeedItem. Used during pre-scan and during live scrolling.

### Tier 1 — Signature Match (< 5ms)

- SimHash of normalised caption against local `ad_signatures` table.
- If match with confidence > 0.95, return immediately.
- Expected catch: 40–60% of ads.

### Tier 2 — Label Detection (< 15ms)

- Check `FeedItem.labelText` against known patterns: `{"Sponsored", "Ad", "Paid partnership", "Promoted", "Anzeige", "Sponsorisé", "Sponsorizzato", "Reklame"}`.
- If match: `OFFICIAL_AD`, confidence 1.0.
- Expected catch: 30–40% of remaining ads.

### Tier 3 — Content Analysis (< 50ms)

- On-device TFLite model (`scrollshield_classifier.tflite`).
- Input: `[captionText] [SEP] [hashtags joined] [SEP] [creatorName]`.
- Output: 6-class probability vector + 20-dimensional topic vector.
- Architecture: DistilBERT-tiny (4L/128H, ~15MB).
- Max probability < 0.7 → `LOW_CONFIDENCE`, fail open.

### Skip Decision (computed as part of classification)

After classification, immediately compute the skip decision based on the active profile:

```kotlin
fun computeSkipDecision(item: ClassifiedItem, profile: UserProfile): SkipDecision {
    if (item.confidence < 0.7) return SHOW_LOW_CONF
    if (item.classification == OFFICIAL_AD) return SKIP_AD
    if (item.classification == INFLUENCER_PROMO
        && INFLUENCER_PROMO in profile.blockedClassifications) return SKIP_AD
    if (item.topicCategory in profile.blockedCategories) return SKIP_BLOCKED
    if (profile.isChildProfile
        && item.classification in setOf(ENGAGEMENT_BAIT, OUTRAGE_TRIGGER)) return SKIP_CHILD
    return SHOW
}
```

This decision is stored in the `ClassifiedItem` and referenced later when the user reaches that position in the feed.

**Acceptance criteria**:

- Tier 1: < 5ms with 100K entries.
- Tier 2: identifies all listed localised labels.
- Tier 3: loads < 2s cold start, inference < 50ms on Snapdragon 7-series.
- Full pipeline: < 60ms worst case.
- F1: > 85% OFFICIAL_AD, > 75% INFLUENCER_PROMO.
- Skip decision computed in < 1ms.

---

## Module 3: Ad Counter

**What**: Persistent floating overlay that counts every ad served during a session. Feature 1. Purely observational.

### Session Management

- Starts on `APP_FOREGROUND`. Loads active profile. Resets counter.
- Ends on `APP_BACKGROUND`. Writes SessionRecord, optionally shows satisfaction survey.
- Checkpoint every 60s.

### Counting Logic

- Subscribes to ClassifiedItem events from both the pre-scan phase and live scrolling.
- Increments on `OFFICIAL_AD` or `INFLUENCER_PROMO`.
- Tracks brands, categories.
- Revenue: `adCount * platformCPM / 1000` (TikTok $10, Instagram $12, YouTube $15).
- When mask is active: displays "Ads detected: X" and "Ads skipped: Y".

### UI — Pill (collapsed)

- Floating `TYPE_APPLICATION_OVERLAY`, anchored top-center.
- Semi-transparent dark blur.
- L→R: status dot | ad count (bold white) | separator | revenue (muted) | separator | session time (muted).
- Status dot: green (0–2), amber (3–10), red (> 10) with pulse animation.
- Monospace 11sp, height 32dp, radius 16dp.
- Draggable, remembers position.
- Tap to expand.

### UI — Summary Card (expanded)

- Bottom-anchored, 40% height.
- Shows: ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration.
- Close → collapse. Export → JSON to Downloads.

### Time Budget Nudges

Passive visual changes on the pill — no blocking:

- 80%: "5 min left" text.
- 100%: flash + "Budget reached".
- 120%: pill background turns red.
- Child profile at 100%: "Time's up" — mask skips all remaining content.

**Acceptance criteria**:

- Renders correctly above TikTok, Instagram, YouTube.
- Increments within 200ms of classification.
- Shows detected and skipped counts when mask active.
- Correct status dot thresholds.
- Draggable, position persists.
- Valid JSON export.
- No touch interception on underlying app.
- Budget nudges at correct thresholds.
- Child hard stop at 100%.

---

## Module 4: Scroll Mask

**What**: Transparent interceptor that pre-scans the feed ahead of the user and auto-skips unwanted items before the user reaches them. Feature 2. The user stays in the native app with full video at all times.

### Pre-Scan Phase (session start)

This is the core innovation. When the user opens a target app with the mask enabled:

1. **Show loading overlay**: Full-screen branded animation over the app. Shield icon with progress indicator. Text: "ScrollShield is preparing your feed." This overlay blocks interaction with the app during the pre-scan.

2. **Fast-forward scan**: Call `feedInterceptionService.scrollForwardFast(bufferSize)` where `bufferSize` is configurable (default: 10). This rapidly scrolls through the first 10 items in the feed. For each item encountered:
   - The feed interception service captures a FeedItem.
   - The classification pipeline classifies it and computes the skip decision.
   - The result is stored in the ScanMap.

3. **Rewind to start**: Call `feedInterceptionService.scrollBackwardFast(bufferSize)` to scroll back to the first video. The feed is now in its original position, but ScrollShield has a complete map of what's ahead.

4. **Dismiss loading overlay**: The user now sees the first video. ScrollShield is ready.

**Expected pre-scan duration**: 10 items × (200ms gesture interval + 60ms classification) = ~2.6 seconds, plus ~2 seconds for the rewind. Total: **~5 seconds**. Masked by the loading animation.

### Live Interception Phase (during scrolling)

Once the pre-scan is complete and the user is scrolling:

1. **On each user swipe**: The feed interception service detects the scroll and reports the new feed position.

2. **Check ScanMap**: Look up the item at the new position. If the pre-computed `skipDecision` is any `SKIP_*` variant:
   - Immediately call `feedInterceptionService.scrollForward()` to advance past it.
   - Show a brief skip flash overlay (200ms): shield icon + "Skipped: Ad" or "Skipped: [category]".
   - The ad counter records the skipped item.
   - If the next item is also flagged, skip again (consecutive skip handling).

3. **If the item is SHOW or SHOW_LOW_CONF**: Do nothing. The video plays normally.

4. **Lookahead extension**: When the user reaches position `scanHead - 3` (3 items from the edge of the pre-scanned buffer), trigger a background extension scan:
   - Briefly suppress user scrolling (optional, may not be needed if extension is fast).
   - Scan the next `extensionSize` items (default: 5) ahead.
   - Add results to ScanMap.
   - User never catches up to unscanned territory.

### Consecutive Skip Handling

If multiple adjacent items are flagged (common during high-ad-pressure periods):

- Execute skips in rapid succession at 300ms intervals.
- Show a single combined flash: "Skipped 3 ads" rather than three separate flashes.
- The user sees a brief fast-forward effect and lands on the next clean item.

### Lookahead Extension Details

```
User position:  [0] [1] [2] [3] [4] [5] [6] [7] [8] [9]
Pre-scanned:    [✓] [✓] [✓] [✓] [✓] [✓] [✓] [✓] [✓] [✓]
                                              ^user is here
                                                       ^scan head

User reaches position 7 (scanHead - 3 = trigger):
  → Background extension scans positions 10–14
  → ScanMap now covers 0–14
  → User continues scrolling unaware
```

The extension scan uses the same fast-forward technique as the initial pre-scan but operates on items beyond the user's current position. Because the feed back-stack allows forward and backward navigation, the extension scrolls ahead, classifies, then scrolls back to the user's current position. If the extension is fast enough (5 items × 260ms ≈ 1.3 seconds), it completes before the user naturally scrolls 3 more items.

### Edge Case: User Scrolls Backward

If the user swipes down to revisit a previous video, the ScanMap already has that item classified. No additional work needed. The skip decision was made during the original pre-scan.

### Edge Case: User Scrolls Faster Than Extension

If the user rapidly swipes and reaches the edge of the ScanMap before the extension completes, ScrollShield does NOT fall back to a degraded live mode. Instead, it re-engages the same pre-scan flow used at session start:

1. The loading overlay reappears: "ScrollShield is scanning ahead."
2. A new pre-scan runs from the current position: fast-forward `bufferSize` items, classify each, rewind.
3. Loading overlay dismisses. User continues scrolling with a full pre-scanned buffer ahead of them.

This means the user has one consistent experience throughout the session: either they're scrolling through pre-scanned content, or they're seeing the shield animation while ScrollShield catches up. There is never a moment where unscanned content reaches the screen. For child profiles this is especially important — the child never sees an unvetted item, period.

The re-scan takes the same ~5 seconds as the initial scan. In practice this should be rare — the lookahead extension is designed to stay ahead of normal scrolling speed. The re-scan only triggers if someone deliberately speed-swipes through content faster than the extension can keep up.

### Loading Overlay UI

- Full-screen overlay, dark background with subtle blur of the app beneath.
- Center: animated shield icon (pulsing or rotating).
- Below: progress bar showing pre-scan progress (e.g., "Scanning 4/10").
- Below progress: "ScrollShield is preparing your feed".
- Below that (small, muted): "Filtering ads and unwanted content".
- For child profile: "Setting up a safe feed for [child name]".

### Skip Flash UI

- Small overlay at bottom of screen, semi-transparent.
- Shield icon + text: "Skipped: Ad" or "Skipped: Gambling" etc.
- Duration: 200ms, fades out.
- For consecutive skips: "Skipped 3 ads" with count.

### Child Profile Behaviour

- Mask always active, cannot be dismissed without PIN.
- Pre-scan blocks: OFFICIAL_AD, INFLUENCER_PROMO, ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, plus all items in blocked categories.
- At time budget 100%: mask begins skipping ALL content (every swipe triggers a skip). Effectively locks the app. The skip flash shows "Time's up — ask a parent".
- Loading overlay uses child-friendly language and animation.

### Interest Learning (session end)

- Runs at session end for default profile only, not child profiles.
- Computes which items the user watched fully vs. manually skipped.
- Updates interest vector: `interestVector = 0.95 * interestVector + 0.05 * mean(watchedItemTopicVectors)`.

### Configuration

```
preScanBufferSize = 10         // Items to pre-scan at session start
extensionSize = 5              // Items to scan per lookahead extension
extensionTriggerDistance = 3   // Trigger extension when user is this close to scan head
skipFlashDurationMs = 200
consecutiveSkipIntervalMs = 300
gestureIntervalMs = 200        // Pause between gestures during pre-scan
gestureDurationMs = 100        // Duration of each swipe gesture during pre-scan
liveGestureDurationMs = 150    // Duration of skip gesture during live mode
```

**Acceptance criteria**:

- Loading overlay appears within 500ms of target app foregrounding.
- Pre-scan of 10 items completes in < 6 seconds.
- Rewind returns to the first video accurately.
- Loading overlay dismisses and user sees first video.
- Pre-computed skip decisions execute with zero additional classification latency.
- `performSkip()` advances to next video within 500ms.
- Skip flash displays for 200ms and fades.
- Consecutive skips batch correctly with combined flash.
- Lookahead extension triggers at correct distance from scan head.
- Extension completes before user reaches scan head in normal scrolling.
- When user outruns buffer, loading overlay reappears and a new pre-scan runs from current position.
- User never sees unscanned content. No degraded mode exists.
- Backward scrolling uses pre-computed decisions (no re-classification).
- Child profile: mask not dismissable without PIN.
- Child profile: hard stop at time budget.
- No visible lag or stutter in native video playback during normal use.
- Pre-scan does not cause TikTok to crash, rate-limit, or degrade feed quality.

---

## Module 5: Onboarding & Settings

### Onboarding (first launch, < 2 min)

1. **Welcome**: value prop + "Get Started".
2. **App selection**: checkboxes for installed target apps.
3. **Interest selection**: 20-topic card grid, select 3–8.
4. **Blocked categories**: toggles for gambling, diet, crypto, political outrage, clickbait, explicit ads. Off by default.
5. **Time budget**: slider per app, 15–120 min, default 30.
6. **Feature toggles**: Ad Counter (on), Scroll Mask (off).
7. **Child profile prompt**: "Will children use this phone?" → if yes, setup child profile.
8. **Permissions**: Accessibility Service + Overlay. Clear explanations.
9. **Done**: confirmation + first-session tip.

### Child Profile Setup

- Pre-selected blocks: gambling, diet, crypto, explicit ads, alcohol.
- Pre-selected classification blocks: ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, INFLUENCER_PROMO.
- Mask always on, not dismissable.
- Time budget default: 15 min/app.
- 4-digit PIN for parent to switch profiles.

### Settings

- Profile switcher at top.
- Per-profile: all onboarding choices editable.
- Advanced: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size.
- Data: export CSV/JSON, delete all data.
- Child profile management: edit, reset, change PIN.

**Acceptance criteria**:

- Onboarding < 2 min.
- Preferences persist.
- Child profile PIN works.
- Settings changes apply next session.
- Delete all data wipes everything.

---

## Module 6: Signature Database & Sync

### Local Store

SQLite `ad_signatures`. Target: 50K–100K entries, < 50MB.

### Sync

- HTTPS REST, delta sync on `last_sync_timestamp`.
- Every 24h on WiFi, or manual.
- Gzipped JSON payload.
- Fully functional without sync.

### Local Learning

- Ads detected not in cache → generate signature, add as `local_detection`.
- Used in subsequent Tier 1 lookups.
- Not shared unless user opts in.

**Acceptance criteria**:

- Lookup < 5ms for 100K entries.
- Delta sync adds/removes correctly.
- WiFi-only default.
- Local detections used in Tier 1.
- Database < 50MB.

---

## Module 7: Session Analytics & Reporting

### Session Recording

- Both features write to SessionRecord.
- Counter: ads detected, brands, categories, revenue, duration, satisfaction.
- Mask adds: ads skipped, items pre-scanned, pre-scan duration.
- Profile ID links to active profile.
- Checkpoint every 60s.

### Weekly Report (Sunday midnight)

- Total time, split by profile.
- Ads detected and skipped.
- Top 5 targeting brands.
- Ad-to-content ratio trend.
- Satisfaction average.
- In-app display, JSON export.

### Child Activity Report

- Parent-facing: child sessions only.
- Time per app, ads blocked, categories encountered, budget compliance.
- Exportable separately.

### Data Retention

- Raw sessions: 90 days → monthly aggregates.
- Aggregates retained indefinitely.

**Acceptance criteria**:

- Session written on every end.
- Checkpoint survives force-kill.
- Weekly report generates correctly.
- Child report shows child sessions only.
- Valid CSV/JSON export.
- Report screen < 1s with 90 days data.

---

## File Structure

```
scrollshield/
├── app/src/main/java/com/scrollshield/
│   ├── ScrollShieldApp.kt
│   ├── di/
│   │   ├── AppModule.kt
│   │   ├── DatabaseModule.kt
│   │   └── ClassificationModule.kt
│   ├── service/
│   │   ├── FeedInterceptionService.kt      # Accessibility service
│   │   │                                    # Content extraction
│   │   │                                    # Gesture dispatch (scroll/skip)
│   │   │                                    # Position tracking
│   │   └── OverlayService.kt               # Foreground service for overlays
│   ├── classification/
│   │   ├── ClassificationPipeline.kt       # 3-tier router
│   │   ├── SignatureMatcher.kt             # Tier 1
│   │   ├── LabelDetector.kt               # Tier 2
│   │   ├── ContentClassifier.kt           # Tier 3 (TFLite)
│   │   └── SkipDecisionEngine.kt          # Computes skip/show per profile
│   ├── feature/
│   │   ├── counter/
│   │   │   ├── AdCounterManager.kt        # Session + counting logic
│   │   │   ├── AdCounterOverlay.kt        # Pill UI
│   │   │   ├── SessionSummaryCard.kt      # Expanded summary
│   │   │   └── TimeBudgetNudge.kt         # Budget visuals
│   │   └── mask/
│   │       ├── ScrollMaskManager.kt       # Orchestrates pre-scan + skip execution
│   │       ├── PreScanController.kt       # Initial + re-scan buffer scan + rewind
│   │       ├── LookaheadExtender.kt       # Background extension scanning
│   │       ├── ScanMap.kt                 # In-memory pre-scan state
│   │       ├── SkipFlashOverlay.kt        # Brief skip animation
│   │       ├── LoadingOverlay.kt          # Pre-scan loading screen (initial + re-scan)
│   │       ├── ConsecutiveSkipHandler.kt  # Batch rapid skips
│   │       └── InterestLearner.kt         # Post-session interest update
│   ├── profile/
│   │   ├── ProfileManager.kt
│   │   ├── ChildProfileConfig.kt
│   │   └── ProfileSwitcher.kt
│   ├── ui/
│   │   ├── onboarding/
│   │   │   ├── OnboardingScreen.kt
│   │   │   ├── InterestSelectionScreen.kt
│   │   │   ├── BlockedCategoryScreen.kt
│   │   │   ├── TimeBudgetScreen.kt
│   │   │   └── ChildProfileSetupScreen.kt
│   │   ├── settings/
│   │   │   └── SettingsScreen.kt
│   │   └── reports/
│   │       ├── ReportScreen.kt
│   │       ├── WeeklyReportCard.kt
│   │       └── ChildActivityReport.kt
│   ├── data/
│   │   ├── model/
│   │   │   ├── FeedItem.kt
│   │   │   ├── ClassifiedItem.kt
│   │   │   ├── SessionRecord.kt
│   │   │   ├── UserProfile.kt
│   │   │   ├── AdSignature.kt
│   │   │   └── ScanMap.kt
│   │   ├── db/
│   │   │   ├── ScrollShieldDatabase.kt
│   │   │   ├── SessionDao.kt
│   │   │   ├── SignatureDao.kt
│   │   │   └── ProfileDao.kt
│   │   ├── preferences/
│   │   │   └── UserPreferencesStore.kt
│   │   └── sync/
│   │       ├── SignatureSyncWorker.kt
│   │       └── SignatureApiClient.kt
│   └── util/
│       ├── SimHash.kt
│       ├── CosineSimilarity.kt
│       └── TextNormaliser.kt
├── app/src/main/assets/
│   └── scrollshield_classifier.tflite
├── app/src/main/res/xml/
│   └── accessibility_service_config.xml
├── app/src/test/
├── app/src/androidTest/
├── ml/
│   ├── train_classifier.py
│   ├── export_tflite.py
│   ├── dataset/
│   └── eval/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Implementation Order

| # | What | Depends On | Est. Effort |
|---|------|------------|-------------|
| 1 | Data Models (incl. ScanMap) | — | 0.5 days |
| 2 | Database + DAOs | 1 | 1 day |
| 3 | Feed Interception Service (incl. gesture dispatch + position tracking) | 1 | 3 days |
| 4 | Classification Pipeline (Tier 2 + skip decision) | 1, 2, 3 | 1.5 days |
| 5 | **Ad Counter** (full feature) | 3, 4 | 2 days |
| 6 | Profile Manager (default + child) | 2 | 1.5 days |
| 7 | Onboarding & Settings | 2, 6 | 2 days |
| 8 | **Scroll Mask — Pre-Scan** (loading overlay, fast-forward scan, rewind, ScanMap) | 3, 4, 6 | 3 days |
| 9 | **Scroll Mask — Live Mode** (skip execution, flash overlay, consecutive handler, lookahead) | 8 | 2.5 days |
| 10 | Classification Tier 1 + Tier 3 | 4 | 2 days |
| 11 | Signature Database & Sync | 2, 4 | 1.5 days |
| 12 | Session Analytics & Reporting (incl. child report) | 2, 5, 9 | 2 days |
| 13 | Integration Testing | All | 2.5 days |

**Total: ~24 days**

**MVP** (steps 1–5): Feed interception + label-based detection + ad counter. Shippable standalone.

**V1** (steps 1–9): Both features with pre-scan buffer and child profiles.

**V1.1** (steps 10–13): Enhanced classification, signatures, analytics.

---

## Technology Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| App | Kotlin, Jetpack Compose | Modern Android, coroutines |
| Feed interception | AccessibilityService API | No root, works with all apps |
| Gesture dispatch | AccessibilityService.dispatchGesture() | Pre-scan scrolling + live skips |
| Overlays | TYPE_APPLICATION_OVERLAY | Counter pill, loading screen, skip flash |
| On-device ML | TensorFlow Lite | Mobile-optimised, NPU support |
| NLP model | DistilBERT-tiny (4L/128H) | Small, good text classification |
| Local storage | Room (SQLite) | Standard Android persistence |
| Preferences | DataStore (Proto) | Type-safe, async |
| Background work | WorkManager | Battery-friendly sync |
| DI | Hilt | Standard Android DI |
| Testing | JUnit5, Espresso, Robolectric | Unit + instrumented + UI |
| ML training | Python, PyTorch, HuggingFace | Pre-export training |

---

## Testing Strategy

### Unit Tests

- Classification: each tier with mock inputs.
- Skip decision engine: all rules, all profile types.
- ScanMap: position tracking, lookahead triggers, edge cases.
- Ad counter: counting, revenue, budget thresholds.
- Pre-scan controller: buffer construction, rewind accuracy.
- Consecutive skip handler: batching logic.

### Integration Tests

- Full pre-scan cycle: fast-forward → classify → rewind → loading overlay dismiss.
- Live skip: user swipe → ScanMap lookup → skip execution → flash.
- Lookahead extension: trigger at correct distance, complete before user catches up.
- Buffer exhaustion: user outruns buffer → loading overlay reappears → new pre-scan from current position → user continues with full buffer.
- Child profile: all blocked types skipped, time budget enforced.
- Session recording on background and force-kill.

### Test Data

- Synthetic feed: 200 items — 60% organic, 20% official ads, 10% influencer promos, 10% engagement bait.
- Known classifications for accuracy tests.
- Child-safety set: 50 items with gambling, diet, outrage — verify child profile blocks all.

### Performance Benchmarks

- Pre-scan of 10 items: < 6 seconds total.
- Rewind of 10 items: < 4 seconds.
- Classification pipeline: < 60ms per item.
- Live skip (ScanMap lookup + gesture): < 500ms.
- Skip flash render: exactly 200ms.
- Signature lookup: < 5ms at 100K entries.
- Cosine similarity: < 1ms per item.
- Counter overlay: zero frame rate impact.

---

## Open Questions

1. **Back-stack depth**: How many items does TikTok keep in its back-stack before evicting old ones? If the limit is low (e.g., 20 items), the rewind after pre-scan must be precise. Test on real devices with different TikTok versions.

2. **Pre-scan detectability**: Does TikTok detect rapid automated scrolling during the pre-scan and penalise the account (shadow ban, degraded recommendations)? The pre-scan mimics natural swipe gestures but at unnatural speed. May need to slow the pre-scan to 300–400ms per item for safety, increasing total time to ~4–5 seconds.

3. **Accessibility tree during fast scroll**: Does the accessibility tree update reliably when scrolling at 200ms intervals? Some apps debounce accessibility events during rapid scrolling. If updates are missed, the pre-scan will have gaps. Test extensively.

4. **Feed position tracking accuracy**: After scrollForwardFast(10) + scrollBackwardFast(10), is the user reliably back at position 0? Off-by-one errors in position tracking would cause the ScanMap to misalign with the actual feed.

5. **Model sizing**: DistilBERT-tiny benchmark needed on Pixel 4a. Consider smaller alternatives if needed.

6. **Battery during pre-scan**: The pre-scan is a burst of activity (rapid gestures + 10 classifications). Measure peak power draw and thermal impact.

7. **Distribution**: Google Play Store rejection risk for AccessibilityService usage. Sideload APK primary, F-Droid secondary. Child-safety angle may help review case.

8. **TikTok version fragmentation**: Accessibility tree structure may differ across TikTok versions. Need a compatibility layer that adapts to different tree layouts. Test on the 3 most recent TikTok versions.