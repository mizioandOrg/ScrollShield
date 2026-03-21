# ScrollShield — Technical Implementation Spec

## Project

- **Name**: ScrollShield
- **Version**: 1.0-draft
- **Date**: March 2026
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

The user never leaves the native app. TikTok renders normally — full video, audio, native UI. ScrollShield is invisible during normal playback; the only visible interruptions are the pre-scan loading animation (~5s) and brief skip flashes. It pre-scans the feed ahead of the user and auto-skips unwanted content before the user reaches it. The user experiences a clean feed from the very first swipe.

### Key Insight: Pre-Scan Buffer

TikTok, Instagram Reels, and YouTube Shorts all maintain a back-stack — you can scroll forward and backward through previously loaded videos. ScrollShield exploits this by pre-scrolling the feed at session start, classifying every item, then scrolling back to the beginning. When the user starts swiping, ScrollShield already knows what's ahead and can skip flagged items with zero latency — the decision was made seconds ago.

This eliminates the timing race between classification and video rendering. There is no subliminal flash of ad content. No racing the renderer. The only cost is a few seconds of loading time at session start, masked by a branded animation.

### Constraints

- All classification and preference logic runs on-device. No user content leaves the phone.
- No root or jailbreak required.
- Target latency for classification pipeline: < 60ms per item.
- Must function fully offline — classification, skip decisions, session recording, and reporting all work offline; only signature sync and data export require connectivity.
- The user always sees native app video. ScrollShield never rebuilds the feed.
- Peak memory usage: < 150MB (see Memory Budget section below for justified breakdown).
- Thermal throttling fallback: if device is overheating, skip Tier 3 classification and rely on Tier 1 + Tier 2 only.

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
    val id: String,                // SHA-256 of (captionText + creatorName + app + feedPosition)
    val timestamp: Long,           // Unix epoch ms
    val app: String,               // Package name of source app
    val creatorName: String,
    val captionText: String,
    val hashtags: List<String>,
    val labelText: String?,        // "Sponsored" / "Ad" label if found
    val screenRegion: Rect,
    val rawNodeDump: String,       // Debug only — stripped in release builds, max 4KB
    val feedPosition: Int,         // Position in the feed back-stack (0 = first loaded)
    val accessibilityNodeId: Long?, // Accessibility node identifier for re-verification
    val detectedDurationMs: Long?  // Duration of content if detectable from accessibility tree
)
```

### ClassifiedItem

```kotlin
data class ClassifiedItem(
    val feedItem: FeedItem,
    val classification: Classification,
    val confidence: Float,         // 0.0 to 1.0
    val topicVector: FloatArray,   // 20-dimensional, maps to TopicCategory entries
    val topicCategory: TopicCategory, // Dominant topic from topicVector argmax
    val tier: Int,                 // Which tier classified it (1, 2, or 3)
    val latencyMs: Long,
    val classifiedAt: Long,        // Unix epoch ms when classification was performed
    val skipDecision: SkipDecision // Pre-computed skip/show decision
)

enum class Classification {
    ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO,
    ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL,
    UNKNOWN  // Unclassifiable — maps to SHOW_LOW_CONF
}

enum class SkipDecision {
    SHOW,           // Let the user see this item
    SKIP_AD,        // Auto-skip: official ad or influencer promo
    SKIP_BLOCKED,   // Auto-skip: blocked category
    SKIP_CHILD,     // Auto-skip: child-unsafe content
    SHOW_LOW_CONF   // Low confidence — fail open, let through
}

enum class TopicCategory(val index: Int, val label: String) {
    COMEDY(0, "Comedy/Humor"), MUSIC(1, "Music/Dance"), FOOD(2, "Food/Cooking"),
    SPORTS(3, "Sports/Fitness"), FASHION(4, "Fashion/Beauty"), TECH(5, "Tech/Science"),
    EDUCATION(6, "Education/Learning"), GAMING(7, "Gaming"), FINANCE(8, "Finance/Business"),
    POLITICS(9, "Politics/Activism"), ANIMALS(10, "Animals/Pets"), TRAVEL(11, "Travel/Adventure"),
    ART(12, "Art/Creativity"), NEWS(13, "News/Current Events"), RELATIONSHIPS(14, "Relationships/Social"),
    CARS(15, "Cars/Automotive"), HOME(16, "Home/DIY"), PARENTING(17, "Parenting/Family"),
    HEALTH(18, "Health/Wellness"), NATURE(19, "Nature/Environment");
    companion object { fun fromIndex(i: Int) = entries.first { it.index == i } }
}
```

### UserProfile

```kotlin
// Room @Entity annotation required — see database module for table definition
data class UserProfile(
    val id: String,
    val name: String,
    val isChildProfile: Boolean,
    val interestVector: FloatArray,        // 20-dimensional
    val blockedCategories: Set<TopicCategory>, // Room TypeConverter: TopicCategorySetConverter
    val blockedClassifications: Set<Classification>,
    val timeBudgets: Map<String, Int>,     // Package name → minutes
    val maskEnabled: Boolean,
    val counterEnabled: Boolean,
    val maskDismissable: Boolean,          // false for child profiles
    val pinProtected: Boolean,
    val parentPinHash: String?,            // SHA-256 of 4-digit PIN + device salt
    val satisfactionHistory: List<Float>,
    val scoringWeights: ScoringWeights,
    val createdAt: Long,                   // Unix epoch ms
    val updatedAt: Long,                   // Unix epoch ms
    val autoActivateSchedule: Pair<LocalTime, LocalTime>? // Start/end time for auto child-mode
)

data class ScoringWeights(
    val interest: Float = 0.35f,
    val wellbeing: Float = 0.25f,
    val novelty: Float = 0.15f,
    val manipulation: Float = 0.25f
)

// Room TypeConverter for Set<TopicCategory>
class TopicCategorySetConverter {
    @TypeConverter
    fun fromSet(categories: Set<TopicCategory>): String = categories.joinToString(",") { it.name }
    @TypeConverter
    fun toSet(value: String): Set<TopicCategory> =
        if (value.isBlank()) emptySet()
        else value.split(",").map { TopicCategory.valueOf(it) }.toSet()
}
```

### SessionRecord

```kotlin
@Entity(tableName = "sessions")
data class SessionRecord(
    @PrimaryKey val id: String,
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
    val preScanDurationMs: Long,           // How long the initial pre-scan took
    val classificationCounts: Map<Classification, Int>, // Breakdown by classification type
    val endedNormally: Boolean             // false if force-killed or checkpointed
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
    val simHash: Long,             // 64-bit SimHash for fast Tier 1 matching
    val confidence: Float,         // Confidence level of the signature (0.0–1.0)
    val firstSeen: Long,
    val expires: Long,
    val source: String,
    val locale: String?            // Locale of the ad content (e.g., "en", "de")
)
```

### ScanMap

The pre-scan buffer's internal state. Not persisted — lives in memory for the duration of a session. All mutable access is guarded by a Mutex to ensure thread safety between the pre-scan coroutine and the user scroll handler.

```kotlin
data class ScanMap(
    val sessionId: String,
    val app: String,
    val items: MutableList<ClassifiedItem>,   // Ordered list of all pre-scanned items
    val scanHead: Int,                        // How far ahead the pre-scan has reached
    val userHead: Int,                        // Where the user currently is in the feed
    val skipIndices: Set<Int>,                // Feed positions flagged for auto-skip
    val isExtending: Boolean = false,         // True while lookahead extension is running
    val lastValidatedHash: String? = null     // Feed fingerprint for re-entry validation
)
```

### ScanMap Lifecycle on App Switch

- **Target-to-target** (e.g., TikTok → Instagram): Discard ScanMap, finalize current session, start new pre-scan in the new app.
- **Return within 60s** (e.g., TikTok → home → TikTok): Retain ScanMap, validate via `lastValidatedHash`. If hash matches, reuse existing ScanMap. If mismatch, discard and re-scan.
- **Return after 60s**: Discard ScanMap and perform a fresh pre-scan.

### lastValidatedHash Algorithm

1. Collect visible `AccessibilityNodeInfo` nodes (TextView, ImageView, Button, View with contentDescription)
2. Extract tuple: `(className, viewIdResourceName, text?.take(64), contentDescription?.take(64))`
3. Sort lexicographically by `viewIdResourceName` then `className`
4. Concatenate with `|` separator
5. SHA-256 hex digest of UTF-8 bytes
- On re-entry: recompute on `TYPE_WINDOW_STATE_CHANGED`, compare; match = reuse ScanMap, mismatch = re-scan
- Performance: <1ms for typical <10KB input

---

## Module 1: Feed Interception Service

**What**: Android AccessibilityService that monitors target apps, extracts feed content, and provides gesture dispatch for pre-scanning and auto-skipping.

**Target packages**: `com.zhiliaoapp.musically` (TikTok), `com.instagram.android`, `com.google.android.youtube`

### Accessibility Service Configuration

`accessibility_service_config.xml`:
- `android:accessibilityEventTypes`: `typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled`
- `android:accessibilityFlags`: `flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews`
- `android:packageNames`: `com.zhiliaoapp.musically,com.instagram.android,com.google.android.youtube`
- `android:notificationTimeout`: `100`
- `android:canRetrieveWindowContent`: `true`
- `android:canPerformGestures`: `true`

### Per-App Extraction Strategy

Each target app has a different accessibility tree structure. The extraction strategy adapts per package:

- **TikTok** (`com.zhiliaoapp.musically`): Creator name from `com.zhiliaoapp.musically:id/title`, caption from `com.zhiliaoapp.musically:id/desc`, "Sponsored" label from `com.zhiliaoapp.musically:id/ad_label`
- **Instagram** (`com.instagram.android`): Creator from `com.instagram.android:id/reel_viewer_title`, caption from `com.instagram.android:id/reel_viewer_caption`, "Sponsored" from `com.instagram.android:id/sponsored_label`
- **YouTube** (`com.google.android.youtube`): Creator from `com.google.android.youtube:id/reel_channel_name`, caption from `com.google.android.youtube:id/reel_multi_format_title`

These resource IDs may change between app versions — see `compat/` package for version-adaptive strategies.

### Core Capabilities

1. **Content extraction**: On content change events, traverse accessibility tree to extract text, creator names, hashtags, ad labels.
2. **Lifecycle events**: Emit `APP_FOREGROUND` and `APP_BACKGROUND` when target apps enter/leave foreground.
3. **Gesture dispatch**: Expose methods for programmatic feed navigation:
   - `scrollForward()`: Dispatch swipe-up gesture to advance to next video.
   - `scrollBackward()`: Dispatch swipe-down gesture to return to previous video.
   - `scrollForwardFast(n)`: Advance n items in rapid succession (for pre-scanning).
   - `scrollBackwardFast(n)`: Return n items (to reset to start after pre-scan).
   - **Error handling**: On gesture dispatch failure, retry once. If retry fails, fall back to live classification mode (no pre-scan).
4. **Position tracking**: Track the user's current position in the feed back-stack. Implementation: integer counter incremented/decremented on scroll events, verified via content fingerprint comparison to detect feed mutations.
5. **Dynamic orientation handling**: Compute gesture coordinates from live screen dimensions (`getRealMetrics()`) rather than fixed values — supports both portrait and landscape.
6. **Modal dialog detection**: During pre-scan, detect modal dialogs (check class hierarchy for `android.app.Dialog`, `isModal` flag). If detected, auto-dismiss with back gesture; timeout after 2s and skip the item.
7. **WebView detection**: If a WebView is detected in the accessibility tree (e.g., in-app browser), pause interception. Resume on WebView close with ScanMap validation via `lastValidatedHash`.

### Gesture Dispatch Details

- Use `AccessibilityService.dispatchGesture()` with `GestureDescription`.
- Swipe-up path: start at (screenWidth/2, screenHeight*0.75), end at (screenWidth/2, screenHeight*0.25).
- Duration per gesture: 100ms for pre-scan mode (fast, user isn't watching), 150ms for live skip mode.
- `scrollForwardFast(n)`: chain n swipe gestures with 200ms pause between each to allow the app to load the next item and the accessibility tree to update.

### Fallback Strategy

If accessibility tree is too shallow for content extraction:

1. Screen-capture OCR via `MediaProjection` API (one-time user permission). Requires a foreground service notification (persistent, low-priority). Uses ML Kit Text Recognition as primary OCR engine.
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
- Battery impact: < 3% additional drain per hour (~20-30mW sustained; <3% of a typical 4000mAh battery per hour).

---

## Module 2: Classification Pipeline

**What**: Three-tier pipeline that classifies each FeedItem. Used during pre-scan and during live scrolling.

### Tier 1 — Signature Match (< 5ms)

- 64-bit SimHash of normalised caption against local `ad_signatures` table.
- Normalisation rules: lowercase, strip emoji, collapse whitespace, remove URLs, remove @mentions.
- Match criteria: Hamming distance ≤ 3 bits between computed SimHash and stored `simHash`.
- If match with confidence > 0.95, return immediately.
- Expected catch: 40–60% of ads.

### Tier 2 — Label Detection (< 15ms)

- Check `FeedItem.labelText` against known patterns (case-insensitive): `{"Sponsored", "Ad", "Paid partnership", "Promoted", "Anzeige", "Sponsorisé", "Sponsorizzato", "Reklame", "広告", "광고", "Patrocinado", "Реклама", "إعلان"}`.
- If match: `OFFICIAL_AD`, confidence 1.0.
- Expected catch: 30–40% of remaining ads.

### Tier 3 — Content Analysis (< 50ms)

- On-device TFLite model (`scrollshield_classifier.tflite`), float16 quantization.
- Tokenizer: WordPiece, max 128 tokens. Input text truncated if longer.
- Input tensor: `int32[1][128]` — token IDs from WordPiece tokenizer of `[captionText] [SEP] [hashtags joined] [SEP] [creatorName]`.
- Output tensors: `float32[1][7]` — 7-class probability vector (including UNKNOWN), `float32[1][20]` — 20-dimensional topic vector.
- Architecture: DistilBERT-tiny (4L/128H, ~15MB).
- Max probability < 0.7 → `UNKNOWN` classification, confidence 0.0, fail open (`SHOW_LOW_CONF`).
- EDUCATIONAL classification is never auto-skipped regardless of profile settings.
- **Error handling**: Catch all Tier 3 exceptions (model load failure, inference error) → return `UNKNOWN` with confidence 0.0.

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

### Dual-OCR Strategy

When `MediaProjection` fallback is active, OCR is needed to extract text from screen captures:

- **Primary**: ML Kit Text Recognition (requires Google Play Services)
- **Fallback**: Tesseract4Android — used when Play Services is unavailable

### Tesseract4Android Specification

- **Library**: `io.github.nicepay:tesseract4android:4.7.0`
- **Trained data**: `eng.traineddata` (fast variant, ~4.2MB) stored in `assets/tessdata/`, copied to `filesDir/tessdata/` on first use
- **APK impact**: ~11MB total (native `.so` ~6.8MB + trained data ~4.2MB)
- **Latency**: ML Kit 35-60ms vs Tesseract 180-350ms per frame
- **Mitigations**: Crop capture to ROI before OCR, run in background coroutine, cache per-node OCR results
- **Accuracy**: ~5-8% higher word error rate than ML Kit; acceptable for classification purposes
- **Language**: English only for V1; additional language packs available as post-launch downloads

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
- Ends on `APP_BACKGROUND`. Writes SessionRecord, shows post-session satisfaction survey (1-5 stars, auto-dismiss after 10s, stored in SessionRecord).
- Checkpoint every 60s: write partial SessionRecord to Room with `endedNormally = false`. Overwritten on normal session end.

### Counting Logic

- Subscribes to ClassifiedItem events from both the pre-scan phase and live scrolling.
- Increments on `OFFICIAL_AD` or `INFLUENCER_PROMO`.
- Tracks brands, categories.
- Revenue: `adCount * platformCPM / 1000` (TikTok $10, Instagram $12, YouTube $15). CPM values are configurable in Settings > Advanced. Note: these are rough estimates based on public CPM averages and do not represent actual revenue earned by the platform.
- When mask is active: displays "Ads detected: X" and "Ads skipped: Y".

### Overlay Permission

- Check `Settings.canDrawOverlays()` before rendering any overlay.
- If not granted, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` intent with package URI.
- Touch passthrough: all overlays use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` to avoid intercepting touches meant for the underlying app.

### UI — Pill (collapsed)

- Floating `TYPE_APPLICATION_OVERLAY`, anchored top-center.
- Semi-transparent dark blur.
- L→R: status dot | ad count (bold white) | separator | revenue (muted) | separator | session time (muted).
- Status dot: green (0–2), amber (3–10), red (> 10) with pulse animation. Thresholds configurable in Settings > Advanced.
- Monospace 11sp, height 32dp, radius 16dp.
- Draggable, remembers position.
- Tap to expand.

### UI — Summary Card (expanded)

- Bottom-anchored, 40% height.
- Shows: ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration.
- Close → collapse. Export → JSON to Downloads. JSON schema includes a `version` key for forward compatibility.

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

3. **Rewind to start**: Call `feedInterceptionService.scrollBackwardFast(bufferSize)` to scroll back to the first video. The feed is now in its original position, but ScrollShield has a complete map of what's ahead. **Feed mutation risk**: After rewind, verify position 0 fingerprint matches the pre-scan snapshot. If the platform has mutated the feed during the pre-scan, re-scan from the new position 0.

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
   - No scroll suppression — extension runs in a background coroutine.
   - Scan the next `extensionSize` items (default: 5) ahead.
   - Add results to ScanMap.
   - If user catches up before extension completes, re-engage the full pre-scan flow (loading overlay reappears).
   - User never catches up to unscanned territory.

### Consecutive Skip Handling

If multiple adjacent items are flagged (common during high-ad-pressure periods):

- Execute skips in rapid succession at 300ms intervals.
- Show a single combined flash: "Skipped 3 ads" rather than three separate flashes.
- The user sees a brief fast-forward effect and lands on the next clean item.
- **Maximum consecutive skips**: `maxConsecutiveSkips = 5`. If more than 5 consecutive items would be skipped, pause and show "High ad density detected" overlay. Resume on user tap.

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

**Note on scroll-back visibility**: During lookahead extension, the user may briefly see the feed jump forward and back. An alternative approach using `MediaProjection` to capture frames without scrolling the visible feed is an open question (see Open Questions). For V1, the brief visual disruption during lookahead is accepted as a known trade-off.

### Edge Case: Back-Stack Limit

If the target app has a shallow back-stack limit, `scrollForward` may detect duplicate items (indicating the end of the back-stack). In this case:
- Stop the pre-scan early and adjust `extensionTriggerDistance` to trigger earlier.
- The `isOwnGesture` flag distinguishes user-initiated scrolls from programmatic scrolls during pre-scan and extension.

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
- Updates interest vector: `interestVector = (1 - alpha) * interestVector + alpha * mean(watchedItemTopicVectors)`.
- Alpha values: `alpha = 0.05` for normal sessions (intentionally conservative to avoid rapid drift), `alpha = 0.2` for the first 5 sessions (faster initial calibration).

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
maxConsecutiveSkips = 5        // Pause after this many consecutive skips
```

### Safe Mode

When both the ad counter and scroll mask features are disabled by the user, ScrollShield enters **safe mode**: observation-only. The accessibility service remains active for session recording and analytics but performs no gesture dispatch or overlay rendering.

### Re-Ranking (Deferred to V2)

Re-ranking feed items based on user interest scores is deferred to V2. The current architecture cannot reorder the feed without rebuilding the native app's UI, which violates the core design principle. V2 may explore alternative approaches such as selective pre-fetching.

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
2. **App selection**: checkboxes for installed target apps. Detection: `PackageManager.getInstalledApplications()` filtered against target package list.
3. **Interest selection**: 20-topic card grid, select 3–8.
4. **Blocked categories**: toggles for gambling, diet, crypto, political outrage, clickbait, explicit ads. Off by default.
5. **Time budget**: slider per app, 15–120 min, default 30. Includes "Unlimited" option.
6. **Feature toggles**: Ad Counter (on), Scroll Mask (off).
7. **Child profile prompt**: "Will children use this phone?" → if yes, setup child profile.
8. **Permissions**: Accessibility Service + Overlay. Clear explanations. For accessibility service: navigate user to system settings, verify activation on return, re-prompt with explanation if not enabled.
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
- Advanced: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size, status dot thresholds.
- Data: export CSV/JSON, delete all data.
- Child profile management: edit, reset, change PIN.
- **Settings timing**: Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session. Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active.
- **Authentication**: Access to child profile settings requires biometric or PIN authentication.

**Acceptance criteria**:

- Onboarding < 2 min.
- Preferences persist.
- Child profile PIN works.
- Settings changes apply at correct timing (next session vs. immediate).
- Biometric/PIN required for child profile settings.
- Delete all data wipes everything.

---

## Module 6: Signature Database & Sync

### Local Store

SQLite `ad_signatures`. Target: 50K–100K entries, < 50MB.

### Sync

- Endpoint: `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- HTTPS REST, delta sync on `last_sync_timestamp`.
- Every 24h on WiFi, or manual.
- Gzipped JSON payload.
- Conflict resolution: prefer `synced` source over `local_detection` when signatures overlap.
- Database migration: Room auto-migrations with server schema version check.
- Fully functional without sync.

### Local Learning

- Ads detected not in cache → generate signature, add as `local_detection`.
- Used in subsequent Tier 1 lookups.
- Not shared unless user opts in.

### Expiry Cleanup

- Nightly WorkManager task removes expired signatures (`expires < currentTime`).
- Runs as a one-time work request rescheduled daily.

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

### Weekly Report

- Generated via WorkManager 7-day PeriodicWorkRequest, anchored to Sunday midnight in device timezone.
- Total time, split by profile.
- Ads detected and skipped.
- Top 5 targeting brands.
- Ad-to-content ratio trend.
- Satisfaction average.
- In-app display, JSON export.
- Triggers a notification: "Your weekly ScrollShield report is ready".

### Monthly Aggregates

- Computed at month end: total sessions, total duration, total ads detected, total ads skipped, average satisfaction, per-app breakdown, top 10 ad brands.
- Stored as summary records for long-term trend analysis.

### Child Activity Report

- Parent-facing: child sessions only.
- Time per app, ads blocked, categories encountered, budget compliance.
- On-demand generation from Reports screen.
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
- Report screen < 1s with 90 days data. Performance: `@RawQuery` with aggregation SQL, index on `(profileId, startTime)`.
- Weekly report notification delivered on schedule.

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
│   │   └── OverlayService.kt               # Single overlay lifecycle manager
│   │                                        # (pill, loading, skip flash, summary)
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
│   ├── error/
│   │   ├── ErrorRecoveryManager.kt          # Centralized error handling + recovery
│   │   └── DiagnosticLogger.kt              # Structured logging for debugging
│   ├── compat/
│   │   ├── AppCompatLayer.kt                # Base class for per-app compatibility
│   │   ├── TikTokCompat.kt                  # TikTok-specific resource IDs + tree traversal
│   │   ├── InstagramCompat.kt               # Instagram-specific resource IDs + tree traversal
│   │   └── YouTubeCompat.kt                 # YouTube-specific resource IDs + tree traversal
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
| 1 | Data Models | — | 0.5 days |
| 2 | Database + DAOs | 1 | 1 day |
| 3 | Feed Interception Service | 1 | 3 days |
| 3b | MediaProjection Fallback | 3 | 1.5 days |
| 4 | Classification — Tier 2 + Skip Decision | 1, 2, 3 | 1.5 days |
| 4b | Classification — Tier 1 + Tier 3 | 4 | 2 days |
| 5 | **Ad Counter** (full feature) | 3, 4 | 2 days |
| 6 | Profile Manager (default + child) | 2 | 1.5 days |
| 7 | Onboarding & Settings | 2, 6 | 2 days |
| 8 | **Scroll Mask — Pre-Scan** (loading overlay, fast-forward scan, rewind, ScanMap) | 3, 4, 6 | 3 days |
| 9 | **Scroll Mask — Live Mode** (skip execution, flash overlay, consecutive handler, lookahead) | 8 | 2.5 days |
| 10 | Signature Database & Sync | 2, 4b | 1.5 days |
| 11 | Session Analytics & Reporting (incl. child report) | 2, 5, 9 | 2 days |
| 12 | Integration Testing | All | 2.5 days |

**Total: ~25 days.** Step 3b runs parallel with step 4. Steps 5/8/9 depend on step 4, not 4b. Step 10 depends on 4b.

**MVP** (steps 1–5): Feed interception + label-based detection + ad counter. Shippable standalone.

**V1** (steps 1–9): Both features with pre-scan buffer and child profiles.

**V1.1** (steps 10–12): Enhanced classification, signatures, analytics, integration testing.

---

## Technology Stack

| Component | Technology | Min Version | Why |
|-----------|-----------|-------------|-----|
| App | Kotlin, Jetpack Compose | Compose BOM 2024.01+ | Modern Android, coroutines |
| Concurrency | Kotlin Coroutines + Flow | — | Structured concurrency for pre-scan, extension, classification |
| Feed interception | AccessibilityService API | — | No root, works with all apps |
| Gesture dispatch | AccessibilityService.dispatchGesture() | — | Pre-scan scrolling + live skips |
| Overlays | TYPE_APPLICATION_OVERLAY | — | Counter pill, loading screen, skip flash |
| On-device ML | TensorFlow Lite | 2.14+ | Mobile-optimised, NPU support |
| OCR | ML Kit Text Recognition / Tesseract4Android | ML Kit 16.0+ | Dual-OCR for MediaProjection fallback |
| NLP model | DistilBERT-tiny (4L/128H) | — | Small, good text classification |
| Local storage | Room (SQLite) | 2.6+ | Standard Android persistence |
| Preferences | DataStore (Proto) | — | Type-safe, async |
| Background work | WorkManager | 2.9+ | Battery-friendly sync |
| DI | Hilt | 2.50+ | Standard Android DI |
| Testing | JUnit5, Espresso, Robolectric | — | Unit + instrumented + UI |
| ML training | Python, PyTorch, HuggingFace | — | Pre-export training |

---

## Testing Strategy

### Unit Tests

- Classification: each tier with mock inputs.
- Skip decision engine: all rules, all profile types.
- ScanMap: position tracking, lookahead triggers, edge cases.
- Ad counter: counting, revenue, budget thresholds.
- Pre-scan controller: buffer construction, rewind accuracy.
- Consecutive skip handler: batching logic.
- Accessibility service mock: Robolectric `ShadowAccessibilityService` for unit tests, plus instrumented tests on real devices.

### Integration Tests

- Full pre-scan cycle: fast-forward → classify → rewind → loading overlay dismiss.
- Live skip: user swipe → ScanMap lookup → skip execution → flash.
- Lookahead extension: trigger at correct distance, complete before user catches up.
- Buffer exhaustion: user outruns buffer → loading overlay reappears → new pre-scan from current position → user continues with full buffer.
- Child profile: all blocked types skipped, time budget enforced.
- Session recording on background and force-kill.

### App Update Regression

- Saved accessibility tree snapshot suite for each target app version.
- Run against `compat/` extractors to detect breakage on app updates.

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

### Performance Regression

- CI benchmark suite: fail build if median classification latency exceeds 80ms.

### Privacy Tests

- Verify zero network calls during classification, session recording, and reporting flows.
- Only signature sync and data export may make network calls.

---

## Memory Budget (150MB Peak)

| Component | Estimated Usage |
|-----------|----------------|
| TFLite model (loaded) | ~15MB |
| TFLite interpreter | ~10MB |
| SimHash index (100K entries) | ~12MB |
| ScanMap (10-item buffer) | <1MB |
| Room database connection | ~5MB |
| Overlay rendering (Compose) | ~15MB |
| Accessibility tree cache | ~8MB |
| Coroutines + Flow buffers | ~10MB |
| **Headroom** | **~74MB** |
| **Total** | **~150MB** |

### Low-Memory Fallback (<4GB device RAM)

- Reduce pre-scan buffer to 5 items.
- Disable lookahead extension (rely on re-scan when user reaches edge).
- Priority: maintain classification accuracy over buffer size.

---

## Security & Privacy

### On-Device Enforcement

- All classification, skip decisions, session recording, and reporting run entirely on-device.
- ProGuard rules strip HTTP client classes outside the signature sync module — prevents accidental network calls from other components.

### Data at Rest

- Optional SQLCipher encryption for Room database (user-configurable in Settings > Advanced).
- `EncryptedSharedPreferences` for DataStore preferences containing sensitive data (PIN hash, profile settings).

### PIN Security

- Parent PIN stored as SHA-256 hash with device-specific salt (from `Settings.Secure.ANDROID_ID`).
- Lockout escalation: 3 failed attempts → 30s lockout, 5 → 5min, 10 → 30min.

### Accessibility Service Scope

- `onAccessibilityEvent()` performs early return for non-target packages — no data captured from other apps.
- Service processes zero data when in the background or when no target app is in the foreground.

### Export Security

- Exported JSON/CSV contains no raw content (captions, creator names).
- Only aggregated statistics: counts, durations, categories, brand names.

---

## Error Handling & Recovery

### Accessibility Service Disconnection

- Android may kill the accessibility service under memory pressure.
- On disconnect: show persistent notification "ScrollShield protection paused".
- On reconnect: restart service, validate ScanMap if session was active.

### TFLite Model Load Failure

- If model file is corrupted or missing: fall back to Tier 1 + Tier 2 only.
- Log diagnostic event. Show subtle indicator on pill overlay.

### Database Corruption

- Room database corruption: delete and recreate database.
- DataStore preferences survive database reset (stored separately).
- Log corruption event for diagnostics.

### Gesture Dispatch Failure

- Track consecutive gesture failures.
- After 3 consecutive failures: disable scroll mask for the remainder of the session.
- Show notification: "Scroll protection paused — feed interaction failed".

### Pre-Scan Timeout

- If pre-scan does not complete within 15 seconds: abort pre-scan.
- Fall back to live classification mode (classify each item as the user scrolls to it).
- Show brief notification on the loading overlay: "Pre-scan timed out — running in live mode".

---

## Open Questions

1. **Back-stack depth**: How many items does TikTok keep in its back-stack before evicting old ones? If the limit is low (e.g., 20 items), the rewind after pre-scan must be precise. Test on real devices with different TikTok versions. *Resolution strategy*: Implement back-stack limit detection (see Edge Case: Back-Stack Limit). If limit < 10, reduce `preScanBufferSize` dynamically.

2. **Pre-scan detectability**: Does TikTok detect rapid automated scrolling during the pre-scan and penalise the account (shadow ban, degraded recommendations)? The pre-scan mimics natural swipe gestures but at unnatural speed. May need to slow the pre-scan to 300–400ms per item for safety, increasing total time to ~4–5 seconds. *Resolution strategy*: A/B test with 200ms vs 350ms intervals on test accounts over 2 weeks. Monitor recommendation quality via topic diversity metric.

3. **Accessibility tree during fast scroll**: Does the accessibility tree update reliably when scrolling at 200ms intervals? Some apps debounce accessibility events during rapid scrolling. If updates are missed, the pre-scan will have gaps. Test extensively. *Resolution strategy*: Implement retry with backoff — if tree unchanged after gesture, wait 100ms and re-read before advancing.

4. **Feed position tracking accuracy**: After scrollForwardFast(10) + scrollBackwardFast(10), is the user reliably back at position 0? Off-by-one errors in position tracking would cause the ScanMap to misalign with the actual feed.

5. **Model sizing**: DistilBERT-tiny benchmark needed on Pixel 4a. Consider smaller alternatives if needed.

6. **Battery during pre-scan**: The pre-scan is a burst of activity (rapid gestures + 10 classifications). Measure peak power draw and thermal impact.

7. **Distribution**: Google Play Store rejection risk for AccessibilityService usage. Sideload APK primary, F-Droid secondary. Child-safety angle may help review case. *Resolution strategy*: Prepare Play Store appeal emphasising parental control use case. Maintain F-Droid and direct APK download as primary distribution from day one.

8. **TikTok version fragmentation**: Accessibility tree structure may differ across TikTok versions. Need a compatibility layer that adapts to different tree layouts. Test on the 3 most recent TikTok versions.

9. **Accessibility service kill by OEMs**: Some Android OEMs (Xiaomi, Huawei, Samsung) aggressively kill background services. May need OEM-specific battery optimization exemption guidance in onboarding.

10. **Feed algorithm adaptation**: Platforms may adapt their feed algorithm if they detect consistent skipping patterns (e.g., serving more ads to compensate). Monitor ad frequency per session over time.

11. **Accessibility service conflicts**: Other apps using accessibility services (password managers, screen readers) may conflict. Test coexistence with TalkBack, LastPass, 1Password.

12. **X/Twitter V2**: X/Twitter uses a non-vertical feed architecture (timeline with mixed media types, quote tweets, threads). Deferred to V2 with dedicated `XCompat` implementation. Key challenges: horizontal scroll within vertical feed, variable-height items, threaded conversations.

---

## Alignment with Proposal

- **Re-ranking**: Deferred to V2. The current accessibility-service architecture cannot reorder native app feeds without rebuilding the UI, which violates the core design principle. See "Re-Ranking (Deferred to V2)" in Module 4.
- **Satisfaction survey**: Fully specified — 1-5 star rating shown post-session, auto-dismiss after 10s, stored in `SessionRecord.satisfactionRating`. Aggregated in weekly and monthly reports.
- **X/Twitter support**: Deferred to V2 with technical justification. Non-vertical feed architecture requires dedicated `XCompat` implementation (see Open Question 12).
- **Agent fleet signature sync**: Aligns with Module 6 — server endpoint provides delta sync, local detections feed back into Tier 1 matching, optional opt-in sharing.