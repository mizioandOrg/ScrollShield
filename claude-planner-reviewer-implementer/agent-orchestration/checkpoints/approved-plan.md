# Approved Plan — ScrollShield Technical Spec Improvement

Approved at Iteration 3 with Score 10/10.

---

## Iteration 1 (Foundation — all changes retained)

### Section: Project (lines 3–9)
- Add `- **Version**: 1.0-draft` and `- **Date**: March 2026`

### Section: Architecture — Core Design Principle (line 47)
- Clarify "invisible agent" → "invisible during normal playback; only visible interruptions are the pre-scan loading animation (~5s) and brief skip flashes"

### Section: Architecture — Constraints (lines 56–61)
- Add thermal throttling fallback: skip Tier 3 if overheating, rely on Tier 1 + 2
- Add memory constraint: peak < 150MB (justified breakdown below)
- Clarify offline scope: classification, skip decisions, session recording, and reporting work offline; only signature sync and data export require connectivity

### Section: Data Models — FeedItem (lines 83–95)
- Add `val accessibilityNodeId: Long?`
- Add `val detectedDurationMs: Long?`
- Clarify `rawNodeDump`: debug only, stripped in release, max 4KB
- Fix SHA-256 collision: hash `(captionText + creatorName + app + feedPosition)`

### Section: Data Models — ClassifiedItem (lines 100–122)
- Add `val classifiedAt: Long`
- Add `topicVector` doc comment mapping to 20 TopicCategory entries
- Add `UNKNOWN` to Classification enum → maps to `SHOW_LOW_CONF`
- Add `val topicCategory: TopicCategory` (dominant topic from topicVector argmax)

### New: TopicCategory enum (20 entries)
```kotlin
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

### Section: Data Models — UserProfile (lines 127–149)
- Add Room `@Entity` annotation note
- Add `val parentPinHash: String?` (SHA-256 of 4-digit PIN)
- Add `val createdAt: Long` and `val updatedAt: Long`
- Add `val autoActivateSchedule: Pair<LocalTime, LocalTime>?`
- Change `blockedCategories: Set<String>` → `blockedCategories: Set<TopicCategory>`
- Add Room `TopicCategorySetConverter` (`@TypeConverter` via `.name`/`valueOf`)

### Section: Data Models — SessionRecord (lines 154–173)
- Add `@Entity(tableName = "sessions")` and `@PrimaryKey val id: String`
- Add `val classificationCounts: Map<Classification, Int>`
- Add `val endedNormally: Boolean`

### Section: Data Models — AdSignature (lines 177–188)
- Add `val simHash: Long`
- Add `val confidence: Float`
- Add `val locale: String?`

### Section: Data Models — ScanMap (lines 194–201)
- Replace full data class:
```kotlin
data class ScanMap(
    val sessionId: String,
    val app: String,
    val items: MutableList<ClassifiedItem>,
    val scanHead: Int,
    val userHead: Int,
    val skipIndices: Set<Int>,
    val isExtending: Boolean = false,
    val lastValidatedHash: String? = null
)
```
- Mutex-guarded mutable access
- Add ScanMap Lifecycle on App Switch specification:
  - Target-to-target: discard ScanMap, finalize session, new pre-scan
  - Return within 60s: retain ScanMap, validate via lastValidatedHash
  - Return after 60s: discard and re-scan

### lastValidatedHash algorithm
1. Collect visible AccessibilityNodeInfo nodes (TextView, ImageView, Button, View with contentDescription)
2. Extract tuple: (className, viewIdResourceName, text?.take(64), contentDescription?.take(64))
3. Sort lexicographically by viewIdResourceName then className
4. Concatenate with `|` separator
5. SHA-256 hex digest of UTF-8 bytes
- Re-entry: recompute on TYPE_WINDOW_STATE_CHANGED, compare; match = reuse, mismatch = re-scan
- Performance: <1ms for typical <10KB input

### Section: Module 1 — Feed Interception Service (lines 205–248)
- Add `accessibility_service_config.xml` details (event types, flags, package names)
- Add per-app extraction strategy (TikTok, Instagram, YouTube resource IDs)
- Add gesture dispatch error handling (retry once, then fallback to live classification)
- Add position tracking implementation (counter + fingerprint verification)
- Add MediaProjection fallback detail (foreground notification, ML Kit OCR)
- Add battery claim justification (~20-30mW sustained, <3% of 4000mAh)
- Add dynamic orientation handling (compute coordinates from live screen dimensions)
- Add modal dialog detection during pre-scan (class hierarchy, isModal, auto-dismiss, 2s timeout)
- Add WebView detection (pause interception, resume on close with ScanMap validation)

### Section: Module 2 — Classification Pipeline (lines 251–301)
- Tier 1: 64-bit SimHash, Hamming distance ≤3, normalisation rules
- Tier 2: Add Japanese, Korean, Portuguese, Russian, Arabic labels; case-insensitive
- Tier 3: WordPiece tokenizer, max 128 tokens, input/output tensor mapping, float16 quantization
- EDUCATIONAL never auto-skipped
- Pipeline error handling: catch Tier 3 exceptions → UNKNOWN + confidence 0.0
- Dual-OCR strategy: ML Kit primary (Play Services), Tesseract4Android fallback

### Tesseract4Android specification
- Library: `io.github.nicepay:tesseract4android:4.7.0`
- Data: `eng.traineddata` (fast variant, ~4.2MB) in `assets/tessdata/` → `filesDir/tessdata/`
- APK impact: ~11MB (native .so ~6.8MB + trained data ~4.2MB)
- Latency: ML Kit 35-60ms vs Tesseract 180-350ms
- Mitigations: crop to ROI, background coroutine, per-node caching
- Accuracy: ~5-8% higher word error rate; acceptable for classification
- English only for V1; additional languages as post-launch downloads

### Section: Module 3 — Ad Counter (lines 305–358)
- Status dot thresholds configurable in Settings > Advanced
- Overlay permission check: `Settings.canDrawOverlays()` → `ACTION_MANAGE_OVERLAY_PERMISSION`
- Revenue CPM values configurable, accuracy note added
- Checkpoint: partial SessionRecord to Room every 60s with `endedNormally = false`
- JSON export schema with `version` key
- Touch passthrough: `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`
- Post-session satisfaction survey: 1-5 stars, auto-dismiss 10s, stored in SessionRecord

### Section: Module 4 — Scroll Mask (lines 362–503)
- Feed mutation risk: verify position 0 fingerprint after rewind
- Back-stack limit fallback: stop if scrollForward detects duplicate, adjust extensionTriggerDistance
- Scroll detection: isOwnGesture flag to distinguish user vs programmatic scrolls
- Lookahead concurrency: no scroll suppression, background coroutine, re-scan if user catches up
- Lookahead scroll-back visibility: note alternative MediaProjection approach as open question
- Interest learning: alpha=0.05 intentional; alpha=0.2 for first 5 sessions
- Add `maxConsecutiveSkips = 5` with "High ad density detected" pause
- Safe mode: observation-only when both features disabled
- Re-ranking deferred to V2 (cannot reorder without rebuilding feed UI)

### Section: Module 5 — Onboarding & Settings (lines 507–544)
- App detection: `PackageManager.getInstalledApplications()` filtered against targets
- Accessibility service activation: navigate to settings, verify on return, re-prompt flow
- Time budget: add "Unlimited" option
- Settings timing: profile changes at next session, feature toggles immediate if no session
- Biometric/PIN auth for child profile settings

### Section: Module 6 — Signature Database & Sync (lines 547–573)
- Sync endpoint: `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- Conflict resolution: prefer synced over local_detection
- Database migration: Room auto-migrations, server schema version
- Expiry cleanup: nightly WorkManager task

### Section: Module 7 — Session Analytics & Reporting (lines 576–614)
- Weekly report: device timezone, WorkManager 7-day PeriodicWorkRequest
- Aggregation: monthly aggregates include total sessions, duration, ads, satisfaction, per-app, top 10 brands
- Child report: on-demand from Reports screen
- Performance: @RawQuery with aggregation SQL, index on (profileId, startTime)
- Weekly report notification

### Section: Implementation Order — Full rewrite
| # | What | Depends On | Est. Effort |
|---|------|------------|-------------|
| 1 | Data Models | — | 0.5 days |
| 2 | Database + DAOs | 1 | 1 day |
| 3 | Feed Interception Service | 1 | 3 days |
| 3b | MediaProjection Fallback | 3 | 1.5 days |
| 4 | Classification — Tier 2 + Skip Decision | 1, 2, 3 | 1.5 days |
| 4b | Classification — Tier 1 + Tier 3 | 4 | 2 days |
| 5 | Ad Counter | 3, 4 | 2 days |
| 6 | Profile Manager | 2 | 1.5 days |
| 7 | Onboarding & Settings | 2, 6 | 2 days |
| 8 | Scroll Mask — Pre-Scan | 3, 4, 6 | 3 days |
| 9 | Scroll Mask — Live Mode | 8 | 2.5 days |
| 10 | Signature Database & Sync | 2, 4b | 1.5 days |
| 11 | Session Analytics & Reporting | 2, 5, 9 | 2 days |
| 12 | Integration Testing | All | 2.5 days |

**Total: ~25 days.** Step 3b parallel with 4. Steps 5/8/9 depend on 4 not 4b. Step 10 depends on 4b.

### Section: Technology Stack
- Add ML Kit / Tesseract4Android row
- Add Kotlin Coroutines + Flow row
- Add minimum library versions: Room 2.6+, Hilt 2.50+, TFLite 2.14+, WorkManager 2.9+, ML Kit 16.0+, Compose BOM 2024.01+

### Section: Testing Strategy
- Accessibility service mock: Robolectric ShadowAccessibilityService + instrumented tests
- App update regression: saved accessibility tree snapshot suite
- Performance regression: CI benchmark, fail if median >80ms
- Privacy tests: zero network calls during classification/recording/reporting

### Section: Memory Budget (150MB)
- TFLite model: ~15MB, interpreter: ~10MB, SimHash index: ~12MB, ScanMap: <1MB, Room: ~5MB, overlays: ~15MB, accessibility cache: ~8MB, coroutines: ~10MB, headroom: ~74MB
- Low-memory fallback (<4GB): reduce buffer to 5, disable lookahead

### New Section: Security & Privacy
- On-device enforcement (ProGuard strips HTTP classes outside sync)
- Data at rest (optional SQLCipher, EncryptedSharedPreferences for DataStore)
- PIN security (SHA-256 + device salt, lockout escalation)
- Accessibility service scope (early return for non-target packages)
- Export security (no raw content, aggregated stats only)

### New Section: Error Handling & Recovery
- Accessibility service disconnection → notification + restart on reconnect
- TFLite model load failure → fallback to Tier 1+2
- Database corruption → delete and recreate, DataStore preferences survive
- Gesture dispatch failure (3x) → disable mask for session
- Pre-scan timeout (15s) → abort, fallback to live classification

### Section: Open Questions
- Resolution strategies for Q1 (back-stack depth), Q2 (detectability), Q3 (tree during fast scroll), Q7 (distribution)
- New Q9: Accessibility service kill by OEMs
- New Q10: Feed algorithm adaptation
- New Q11: Accessibility service conflicts
- New Q12 (from iteration 2): X/Twitter V2 — non-vertical feed architecture challenges

### Section: File Structure
- Clarify OverlayService.kt as single overlay lifecycle manager
- Add `error/` package (ErrorRecoveryManager.kt, DiagnosticLogger.kt)
- Add `compat/` package (AppCompatLayer.kt, TikTokCompat.kt, InstagramCompat.kt, YouTubeCompat.kt)

### Alignment with Proposal
- Re-ranking: deferred to V2 with justification
- Satisfaction survey: specified with UI, timing, and storage
- X/Twitter: deferred to V2 with technical justification
- Agent fleet signature sync: aligns with Module 6
