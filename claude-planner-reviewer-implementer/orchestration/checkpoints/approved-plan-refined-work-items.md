Now I have the full original spec. Let me produce the complete plan addressing all 10 issues from the Iteration 2 critique.

# Complete Plan — Iteration 3

## Overview

Split the ScrollShield technical implementation spec into 15 self-contained work items in `InputData/RefinedWorkItems/`. Each file follows the same template. This iteration addresses all 10 issues from the Iteration 2 critique.

## Changes from Iteration 2

1. **Issue 1 (FeedItem `detectedDurationMs`)**: WI-02 now explicitly lists all 12 fields including `detectedDurationMs: Long?`.
2. **Issue 2 (Open Questions 1-7, 10, 12)**: Each unassigned open question is now referenced as a Note in the most relevant work item (OQ1/OQ3/OQ4 → WI-05, OQ2/OQ6 → WI-09, OQ5 → WI-06, OQ7 → WI-01, OQ10 → WI-13, OQ12 → WI-05).
3. **Issue 3 ("How the Two Features Relate" section)**: Added verbatim to WI-06 as architectural context.
4. **Issue 4 ("Key Insight: Pre-Scan Buffer" section)**: Added verbatim to WI-09.
5. **Issue 5 (Service processes zero data in background)**: Added to WI-05's privacy constraints.
6. **Issue 6 (FeedItem.rawNodeDump debug detail)**: Added "Debug only — stripped in release builds, max 4KB" to WI-02.
7. **Issue 7 (MVP/V1/V1.1 milestones)**: Added a Milestones section to WI-01 mapping work items to MVP/V1/V1.1.
8. **Issue 8 (Module 6 acceptance criteria)**: Added "Database < 50MB" and "WiFi-only default" to WI-12.
9. **Issue 9 (Module 3 acceptance criteria)**: Added "Increments within 200ms of classification" to WI-08.
10. **Issue 10 (Module 4 acceptance criteria)**: Added all 5 missing acceptance criteria to WI-09 and WI-10.

---

## Template

Every work item file uses this structure:

```markdown
# WI-NN: Title

## Source
Which section(s) of the original spec this covers.

## Goal
One-sentence summary.

## Context
Architectural background the implementer needs.

## Dependencies
- **Hard** (must be completed first): list
- **Integration** (must exist at integration time): list

## Files to Create / Modify
Exact file paths.

## Detailed Specification
Verbatim code, constants, algorithms, UI specs.

## Acceptance Criteria
Testable pass/fail conditions.

## Notes
Open questions, caveats, edge cases.
```

---

## Work Item Files

### File: `InputData/RefinedWorkItems/WI-01-project-scaffolding.md`

```markdown
# WI-01: Project Scaffolding & Build Configuration

## Source
- Project section (lines 1–11)
- Technology Stack table
- File Structure
- Implementation Order (milestone mapping)

## Goal
Create the Android project skeleton with all dependencies, modules, and build configuration so that subsequent work items have a compilable base.

## Context
ScrollShield is a single-APK Android app targeting API 28+. Stack: Kotlin, Jetpack Compose, TensorFlow Lite, Room, Hilt. The project uses a single `app` module with package `com.scrollshield`.

## Dependencies
- **Hard**: None — this is the first work item.
- **Integration**: None.

## Files to Create / Modify

- `settings.gradle.kts`
- `build.gradle.kts` (project-level)
- `app/build.gradle.kts` (module-level)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/scrollshield/ScrollShieldApp.kt`
- `app/src/main/java/com/scrollshield/di/AppModule.kt`
- `app/src/main/java/com/scrollshield/di/DatabaseModule.kt`
- `app/src/main/java/com/scrollshield/di/ClassificationModule.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`
- Directory stubs for all packages in the file structure

## Detailed Specification

### `settings.gradle.kts`
```kotlin
rootProject.name = "ScrollShield"
include(":app")
```

### `build.gradle.kts` (project-level)
- Kotlin 1.9+
- Android Gradle Plugin 8.2+
- Hilt Gradle Plugin

### `app/build.gradle.kts`
```
android {
    namespace = "com.scrollshield"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.scrollshield"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-draft"
    }
}
```

Dependencies (with minimum versions from spec):
| Dependency | Min Version |
|---|---|
| Jetpack Compose BOM | 2024.01+ |
| Room | 2.6+ |
| Hilt | 2.50+ |
| TensorFlow Lite | 2.14+ |
| ML Kit Text Recognition | 16.0+ |
| Tesseract4Android | 4.7.0 (`io.github.nicepay:tesseract4android:4.7.0`) |
| WorkManager | 2.9+ |
| DataStore (Proto) | latest stable |
| JUnit5, Espresso, Robolectric | latest stable |
| Kotlin Coroutines + Flow | latest stable |

### `accessibility_service_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:packageNames="com.zhiliaoapp.musically,com.instagram.android,com.google.android.youtube"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true" />
```

### `ScrollShieldApp.kt`
```kotlin
@HiltAndroidApp
class ScrollShieldApp : Application()
```

### DI modules
- `AppModule.kt`: provide application context, coroutine dispatchers
- `DatabaseModule.kt`: provide Room database singleton, all DAOs
- `ClassificationModule.kt`: provide TFLite interpreter, pipeline components

### AndroidManifest.xml
Declare permissions:
- `SYSTEM_ALERT_WINDOW` (overlay)
- `FOREGROUND_SERVICE` (MediaProjection fallback)
- `INTERNET` (signature sync only)
- `ACCESS_NETWORK_STATE` (WiFi check for sync)
- `RECEIVE_BOOT_COMPLETED` (WorkManager)

Declare accessibility service with config reference.

### Milestones (from original spec Implementation Order)

Map work items to delivery milestones:
- **MVP** (original steps 1-5): WI-01, WI-02, WI-03, WI-04, WI-05, WI-06 (Tier 2 + Skip Decision), WI-08
- **V1** (original steps 1-9): MVP + WI-06 (full pipeline), WI-07, WI-09, WI-10, WI-11
- **V1.1** (original steps 10-12): V1 + WI-12, WI-13, WI-14, WI-15

## Acceptance Criteria
- Project compiles with `./gradlew assembleDebug`
- All dependencies resolve
- Empty app launches on API 28+ emulator
- All package directories exist
- Accessibility service config is valid XML

## Notes
- Open Question 7 (Distribution): Google Play Store may reject AccessibilityService usage. Plan for sideload APK as primary, F-Droid as secondary. Prepare Play Store appeal emphasising parental control use case. Maintain F-Droid and direct APK download as primary distribution from day one.
- ProGuard rules should strip HTTP client classes outside the signature sync module (from Security & Privacy section) — configure in this work item but enforcement is validated in WI-14.
```

---

### File: `InputData/RefinedWorkItems/WI-02-data-models.md`

```markdown
# WI-02: Data Models & Enums

## Source
- Data Models section (FeedItem, ClassifiedItem, Classification, SkipDecision, TopicCategory, UserProfile, ScoringWeights, TopicCategorySetConverter, SessionRecord, AdSignature, ScanMap)

## Goal
Implement all data model classes and enums exactly as specified in the original plan.

## Context
These data classes are the shared vocabulary of the entire app. Every other work item depends on these types. They must be implemented verbatim from the spec.

## Dependencies
- **Hard**: WI-01 (project must compile)
- **Integration**: None.

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/data/model/FeedItem.kt`
- `app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt`
- `app/src/main/java/com/scrollshield/data/model/UserProfile.kt`
- `app/src/main/java/com/scrollshield/data/model/SessionRecord.kt`
- `app/src/main/java/com/scrollshield/data/model/AdSignature.kt`
- `app/src/main/java/com/scrollshield/data/model/ScanMap.kt`

## Detailed Specification

### FeedItem (12 fields)
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

Note on `rawNodeDump`: This field is for debugging only. It must be stripped in release builds (via ProGuard/R8 or a build-type conditional) and capped at max 4KB.

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
```

### Classification enum
```kotlin
enum class Classification {
    ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO,
    ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL,
    UNKNOWN  // Unclassifiable — maps to SHOW_LOW_CONF
}
```

### SkipDecision enum
```kotlin
enum class SkipDecision {
    SHOW,           // Let the user see this item
    SKIP_AD,        // Auto-skip: official ad or influencer promo
    SKIP_BLOCKED,   // Auto-skip: blocked category
    SKIP_CHILD,     // Auto-skip: child-unsafe content
    SHOW_LOW_CONF   // Low confidence — fail open, let through
}
```

### TopicCategory enum
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

### UserProfile
```kotlin
data class UserProfile(
    val id: String,
    val name: String,
    val isChildProfile: Boolean,
    val interestVector: FloatArray,        // 20-dimensional
    val blockedCategories: Set<TopicCategory>,
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
    val autoActivateSchedule: Pair<LocalTime, LocalTime>?
)

data class ScoringWeights(
    val interest: Float = 0.35f,
    val wellbeing: Float = 0.25f,
    val novelty: Float = 0.15f,
    val manipulation: Float = 0.25f
)

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
    val itemsPreScanned: Int,
    val adsDetected: Int,
    val adsSkipped: Int,
    val adBrands: List<String>,
    val adCategories: List<String>,
    val estimatedRevenue: Float,
    val satisfactionRating: Int?,
    val maskWasEnabled: Boolean,
    val preScanDurationMs: Long,
    val classificationCounts: Map<Classification, Int>,
    val endedNormally: Boolean
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
    val confidence: Float,         // 0.0–1.0
    val firstSeen: Long,
    val expires: Long,
    val source: String,
    val locale: String?
)
```

### ScanMap
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

Include ScanMap lifecycle rules as KDoc:
- Target-to-target: Discard ScanMap, finalize session, start new pre-scan.
- Return within 60s: Retain ScanMap, validate via `lastValidatedHash`. Match = reuse, mismatch = re-scan.
- Return after 60s: Discard and fresh pre-scan.

### Room @Entity annotations
- `SessionRecord`: `@Entity(tableName = "sessions")`
- `AdSignature`: `@Entity(tableName = "ad_signatures")`
- `UserProfile`: `@Entity` with Room TypeConverters (`TopicCategorySetConverter` and additional converters for `Set<Classification>`, `Map<String, Int>`, `List<Float>`, `ScoringWeights`, `Pair<LocalTime, LocalTime>?`)

## Acceptance Criteria
- All 6 model files compile
- All 12 FeedItem fields present including `detectedDurationMs`
- All enums have correct entries
- `TopicCategory.fromIndex()` works for indices 0-19
- `TopicCategorySetConverter` round-trips correctly
- `ScoringWeights` defaults match spec values (0.35, 0.25, 0.15, 0.25)
- `rawNodeDump` is annotated or documented as debug-only, max 4KB

## Notes
- Room `@Entity` annotations on `UserProfile` require TypeConverters for complex types — implement all converters in this work item.
- `rawNodeDump` must be stripped in release builds and capped at 4KB. The stripping mechanism (ProGuard rule or build-type conditional) should be documented here but enforced in WI-01's build config.
```

---

### File: `InputData/RefinedWorkItems/WI-03-database-daos-preferences.md`

```markdown
# WI-03: Database, DAOs & Preferences Store

## Source
- File Structure: `data/db/`, `data/preferences/`
- Data Models (Room annotations)
- Module 6: Local Store (SQLite)
- Security & Privacy: Data at Rest

## Goal
Implement the Room database, all DAOs, TypeConverters, and the DataStore preferences store.

## Context
Room is the persistence layer for sessions, signatures, and profiles. DataStore (Proto) stores user preferences. The database must support auto-migrations with server schema version check (for signature sync). Optional SQLCipher encryption is user-configurable.

## Dependencies
- **Hard**: WI-02 (data models must exist)
- **Integration**: WI-01 (DI modules provide database singleton)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/data/db/ScrollShieldDatabase.kt`
- `app/src/main/java/com/scrollshield/data/db/SessionDao.kt`
- `app/src/main/java/com/scrollshield/data/db/SignatureDao.kt`
- `app/src/main/java/com/scrollshield/data/db/ProfileDao.kt`
- `app/src/main/java/com/scrollshield/data/preferences/UserPreferencesStore.kt`
- Additional TypeConverter classes as needed

## Detailed Specification

### ScrollShieldDatabase
```kotlin
@Database(
    entities = [SessionRecord::class, AdSignature::class, UserProfile::class],
    version = 1,
    autoMigrations = []
)
@TypeConverters(
    TopicCategorySetConverter::class,
    ClassificationSetConverter::class,
    StringListConverter::class,
    StringIntMapConverter::class,
    ClassificationIntMapConverter::class,
    FloatListConverter::class,
    ScoringWeightsConverter::class,
    LocalTimePairConverter::class
)
abstract class ScrollShieldDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun signatureDao(): SignatureDao
    abstract fun profileDao(): ProfileDao
}
```

### SessionDao
```kotlin
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionRecord)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getSessionsByProfile(profileId: String): Flow<List<SessionRecord>>

    @Query("SELECT * FROM sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: Long): List<SessionRecord>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsByProfileSince(profileId: String, since: Long): List<SessionRecord>

    @Query("DELETE FROM sessions WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
```
Index on `(profileId, startTime)` for report performance (must render < 1s with 90 days data).

### SignatureDao
```kotlin
@Dao
interface SignatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signatures: List<AdSignature>)

    @Query("SELECT * FROM ad_signatures WHERE expires > :now")
    suspend fun getActive(now: Long): List<AdSignature>

    @Query("DELETE FROM ad_signatures WHERE expires < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM ad_signatures")
    suspend fun count(): Int

    @Query("DELETE FROM ad_signatures WHERE source = 'synced' AND id IN (:ids)")
    suspend fun deleteSyncedByIds(ids: List<String>)
}
```

### ProfileDao
```kotlin
@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM UserProfile WHERE id = :id")
    suspend fun getById(id: String): UserProfile?

    @Query("SELECT * FROM UserProfile")
    fun getAllProfiles(): Flow<List<UserProfile>>

    @Delete
    suspend fun delete(profile: UserProfile)
}
```

### UserPreferencesStore
- Backed by DataStore (Proto)
- Uses `EncryptedSharedPreferences` for sensitive data (PIN hash, profile settings)
- Stores: active profile ID, onboarding completed flag, feature toggles, advanced settings (CPM overrides, buffer sizes, status dot thresholds)

### Optional SQLCipher
- User-configurable in Settings > Advanced
- When enabled, use `SupportSQLiteOpenHelper.Factory` from SQLCipher to create encrypted database
- Default: off (performance overhead)

## Acceptance Criteria
- Database creates successfully on first launch
- All CRUD operations work for sessions, signatures, profiles
- TypeConverters round-trip all complex types
- Index on `(profileId, startTime)` exists
- DataStore preferences persist across app restarts
- `EncryptedSharedPreferences` used for sensitive fields
- Database auto-migration infrastructure is in place

## Notes
- Data retention policy (90-day raw sessions → monthly aggregates) is implemented in WI-13, not here. This WI provides the DAO methods (`deleteOlderThan`) that WI-13 will call.
```

---

### File: `InputData/RefinedWorkItems/WI-04-utility-classes.md`

```markdown
# WI-04: Utility Classes

## Source
- File Structure: `util/`
- Module 2: Tier 1 (SimHash, normalisation)
- Data Models: ScanMap (lastValidatedHash algorithm)

## Goal
Implement SimHash, CosineSimilarity, TextNormaliser, and the lastValidatedHash algorithm.

## Context
These utilities are used across multiple modules. SimHash powers Tier 1 signature matching. CosineSimilarity is used for interest vector comparison. TextNormaliser prepares text for hashing and classification. The lastValidatedHash algorithm validates ScanMap reuse on app re-entry.

## Dependencies
- **Hard**: WI-01 (project compiles)
- **Integration**: None.

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/util/SimHash.kt`
- `app/src/main/java/com/scrollshield/util/CosineSimilarity.kt`
- `app/src/main/java/com/scrollshield/util/TextNormaliser.kt`
- `app/src/main/java/com/scrollshield/util/FeedFingerprint.kt` (lastValidatedHash)

## Detailed Specification

### SimHash
- Compute 64-bit SimHash of normalised text
- Input: String (already normalised)
- Output: Long (64-bit hash)
- Comparison: Hamming distance function `fun hammingDistance(a: Long, b: Long): Int`
- Match threshold: Hamming distance ≤ 3 bits

### TextNormaliser
Normalisation rules (from spec):
1. Lowercase
2. Strip emoji (Unicode emoji ranges)
3. Collapse whitespace
4. Remove URLs (regex)
5. Remove @mentions (regex)

```kotlin
fun normalise(text: String): String
```

### CosineSimilarity
```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float
```
- Used for interest vector comparison
- Performance target: < 1ms per item

### FeedFingerprint (lastValidatedHash algorithm)
Verbatim from spec:
1. Collect visible `AccessibilityNodeInfo` nodes (TextView, ImageView, Button, View with contentDescription)
2. Extract tuple: `(className, viewIdResourceName, text?.take(64), contentDescription?.take(64))`
3. Sort lexicographically by `viewIdResourceName` then `className`
4. Concatenate with `|` separator
5. SHA-256 hex digest of UTF-8 bytes

Usage: On re-entry, recompute on `TYPE_WINDOW_STATE_CHANGED`, compare; match = reuse ScanMap, mismatch = re-scan.
Performance: <1ms for typical <10KB input.

## Acceptance Criteria
- SimHash produces consistent 64-bit hashes
- Hamming distance correctly counts differing bits
- TextNormaliser strips emoji, URLs, @mentions, collapses whitespace, lowercases
- CosineSimilarity returns 1.0 for identical vectors, 0.0 for orthogonal
- CosineSimilarity < 1ms per comparison
- FeedFingerprint produces consistent SHA-256 hex digests
- FeedFingerprint handles empty node lists gracefully

## Notes
- Unit tests for all utility functions should be part of this work item.
```

---

### File: `InputData/RefinedWorkItems/WI-05-feed-interception-service.md`

```markdown
# WI-05: Feed Interception Service

## Source
- Module 1: Feed Interception Service (entire section)
- File Structure: `service/FeedInterceptionService.kt`
- Accessibility Service Configuration
- Per-App Extraction Strategy
- Compat layer: `compat/` package

## Goal
Implement the AccessibilityService that monitors target apps, extracts feed content, dispatches gestures, and tracks feed position.

## Context
This is the foundational service that powers both features. It runs as an Android AccessibilityService, monitors TikTok/Instagram/YouTube, extracts FeedItem data from the accessibility tree, and provides gesture dispatch for pre-scanning and auto-skipping. The user never leaves the native app.

## Dependencies
- **Hard**: WI-02 (FeedItem model), WI-04 (FeedFingerprint for lastValidatedHash)
- **Integration**: WI-01 (manifest declares service), WI-09 (pre-scan uses gesture dispatch)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt`
- `app/src/main/java/com/scrollshield/compat/AppCompatLayer.kt`
- `app/src/main/java/com/scrollshield/compat/TikTokCompat.kt`
- `app/src/main/java/com/scrollshield/compat/InstagramCompat.kt`
- `app/src/main/java/com/scrollshield/compat/YouTubeCompat.kt`

## Detailed Specification

### Target packages
- `com.zhiliaoapp.musically` (TikTok)
- `com.instagram.android`
- `com.google.android.youtube`

### Per-App Extraction Strategy
- **TikTok**: Creator name from `com.zhiliaoapp.musically:id/title`, caption from `com.zhiliaoapp.musically:id/desc`, "Sponsored" label from `com.zhiliaoapp.musically:id/ad_label`
- **Instagram**: Creator from `com.instagram.android:id/reel_viewer_title`, caption from `com.instagram.android:id/reel_viewer_caption`, "Sponsored" from `com.instagram.android:id/sponsored_label`
- **YouTube**: Creator from `com.google.android.youtube:id/reel_channel_name`, caption from `com.google.android.youtube:id/reel_multi_format_title`

These resource IDs may change between app versions — the `compat/` package provides version-adaptive strategies.

### Core Capabilities

1. **Content extraction**: On content change events, traverse accessibility tree to extract text, creator names, hashtags, ad labels. Build `FeedItem` with SHA-256 ID of `(captionText + creatorName + app + feedPosition)`.

2. **Lifecycle events**: Emit `APP_FOREGROUND` and `APP_BACKGROUND` when target apps enter/leave foreground.

3. **Gesture dispatch** via `AccessibilityService.dispatchGesture()` with `GestureDescription`:
   - `scrollForward()`: Swipe-up gesture to advance to next video
   - `scrollBackward()`: Swipe-down gesture to return to previous video
   - `scrollForwardFast(n)`: Advance n items in rapid succession (for pre-scanning)
   - `scrollBackwardFast(n)`: Return n items (to reset to start after pre-scan)
   - Swipe-up path: start at `(screenWidth/2, screenHeight*0.75)`, end at `(screenWidth/2, screenHeight*0.25)`
   - Duration: 100ms for pre-scan mode, 150ms for live skip mode
   - `scrollForwardFast(n)`: chain n swipe gestures with 200ms pause between each
   - **Error handling**: On gesture dispatch failure, retry once. If retry fails, fall back to live classification mode (no pre-scan).
   - Dynamic orientation handling: compute coordinates from `getRealMetrics()` rather than fixed values

4. **Position tracking**: Integer counter incremented/decremented on scroll events, verified via content fingerprint comparison to detect feed mutations. The `isOwnGesture` flag distinguishes user-initiated scrolls from programmatic scrolls.

5. **Modal dialog detection**: During pre-scan, detect modal dialogs (check class hierarchy for `android.app.Dialog`, `isModal` flag). If detected, auto-dismiss with back gesture; timeout after 2s and skip the item.

6. **WebView detection**: If a WebView is detected in the accessibility tree (e.g., in-app browser), pause interception. Resume on WebView close with ScanMap validation via `lastValidatedHash`.

### Privacy constraint
- `onAccessibilityEvent()` performs early return for non-target packages — no data captured from other apps.
- **Service processes zero data when in the background or when no target app is in the foreground.** This means no accessibility tree traversal, no gesture dispatch, no classification, and no session recording when the service is running but no target app is foregrounded.

### Fallback Strategy
If accessibility tree is too shallow:
1. Screen-capture OCR via `MediaProjection` API (one-time user permission). Requires foreground service notification (persistent, low-priority). Uses ML Kit Text Recognition as primary OCR engine.
2. If OCR insufficient: degrade to label-only detection (Tier 2).

### Compat Layer
- `AppCompatLayer.kt`: Base abstract class with methods `extractCreator()`, `extractCaption()`, `extractAdLabel()`, `extractHashtags()`
- `TikTokCompat.kt`, `InstagramCompat.kt`, `YouTubeCompat.kt`: Concrete implementations with versioned resource ID mappings

## Acceptance Criteria
- Activates/deactivates on target app foreground/background
- Extracts creator name, caption, hashtags from TikTok (or falls back gracefully)
- Detects "Sponsored" label with > 95% recall
- `scrollForward()` reliably advances TikTok to the next video
- `scrollBackward()` reliably returns to the previous video
- `scrollForwardFast(10)` advances 10 items in < 4 seconds
- `scrollBackwardFast(10)` returns 10 items in < 4 seconds
- Position tracking is accurate after forward/backward navigation
- No ANR under rapid gesture dispatch
- Battery impact: < 3% additional drain per hour (~20-30mW sustained)
- Service processes zero data when no target app is in the foreground

## Notes
- Open Question 1 (Back-stack depth): How many items does TikTok keep in its back-stack before evicting? If limit < 10, reduce `preScanBufferSize` dynamically. Implement back-stack limit detection (detect duplicate items during scrollForward).
- Open Question 3 (Accessibility tree during fast scroll): Does the accessibility tree update reliably at 200ms intervals? Some apps debounce accessibility events. Implement retry with backoff — if tree unchanged after gesture, wait 100ms and re-read before advancing.
- Open Question 4 (Feed position tracking accuracy): After scrollForwardFast(10) + scrollBackwardFast(10), is the user reliably back at position 0? Off-by-one errors would misalign ScanMap. Verify via content fingerprint comparison.
- Open Question 8 (TikTok version fragmentation): Accessibility tree structure may differ across TikTok versions. The compat layer must adapt. Test on the 3 most recent TikTok versions.
- Open Question 9 (Accessibility service kill by OEMs): Some OEMs aggressively kill background services. May need OEM-specific battery optimization exemption guidance in onboarding.
- Open Question 11 (Accessibility service conflicts): Test coexistence with TalkBack, LastPass, 1Password.
- Open Question 12 (X/Twitter V2): X/Twitter uses non-vertical feed architecture. Deferred to V2 with dedicated `XCompat` implementation. Key challenges: horizontal scroll within vertical feed, variable-height items, threaded conversations.
```

---

### File: `InputData/RefinedWorkItems/WI-06-classification-pipeline.md`

```markdown
# WI-06: Classification Pipeline

## Source
- Module 2: Classification Pipeline (entire section)
- File Structure: `classification/`
- Architecture: "How the Two Features Relate" section

## Goal
Implement the three-tier classification pipeline (Signature Match → Label Detection → Content Analysis) and the skip decision engine.

## Context
The classification pipeline is the shared intelligence layer consumed by both features. Both the Ad Counter and Scroll Mask depend on it.

### How the Two Features Relate (verbatim from spec)
- The **Ad Counter** is passive — observes and counts. Never modifies the experience.
- The **Scroll Mask** is active — pre-scans and auto-skips unwanted content.
- Both consume events from the same classification pipeline.
- They run independently or together. Counter is on by default. Mask is opt-in.
- When both are active, the counter shows all ads the platform *attempted* to serve, including those the mask skipped.

## Dependencies
- **Hard**: WI-02 (ClassifiedItem, Classification, SkipDecision, AdSignature), WI-04 (SimHash, TextNormaliser)
- **Integration**: WI-03 (SignatureDao for Tier 1 lookups), WI-05 (FeedItem events from service)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt`
- `app/src/main/java/com/scrollshield/classification/SignatureMatcher.kt`
- `app/src/main/java/com/scrollshield/classification/LabelDetector.kt`
- `app/src/main/java/com/scrollshield/classification/ContentClassifier.kt`
- `app/src/main/java/com/scrollshield/classification/SkipDecisionEngine.kt`
- `app/src/main/assets/scrollshield_classifier.tflite` (placeholder)

## Detailed Specification

### Tier 1 — Signature Match (< 5ms)
- Compute 64-bit SimHash of normalised caption (using TextNormaliser + SimHash from WI-04)
- Compare against local `ad_signatures` table
- Match criteria: Hamming distance ≤ 3 bits
- If match with confidence > 0.95, return immediately
- Expected catch: 40–60% of ads

### Tier 2 — Label Detection (< 15ms)
- Check `FeedItem.labelText` against known patterns (case-insensitive):
  ```
  {"Sponsored", "Ad", "Paid partnership", "Promoted", "Anzeige",
   "Sponsorisé", "Sponsorizzato", "Reklame", "広告", "광고",
   "Patrocinado", "Реклама", "إعلان"}
  ```
- If match: `OFFICIAL_AD`, confidence 1.0
- Expected catch: 30–40% of remaining ads

### Tier 3 — Content Analysis (< 50ms)
- On-device TFLite model (`scrollshield_classifier.tflite`), float16 quantization
- Architecture: DistilBERT-tiny (4L/128H, ~15MB)
- Tokenizer: WordPiece, max 128 tokens. Input text truncated if longer.
- Input tensor: `int32[1][128]` — token IDs from WordPiece tokenizer of `[captionText] [SEP] [hashtags joined] [SEP] [creatorName]`
- Output tensors:
  - `float32[1][7]` — 7-class probability vector (maps to Classification enum including UNKNOWN)
  - `float32[1][20]` — 20-dimensional topic vector (maps to TopicCategory)
- Max probability < 0.7 → `UNKNOWN` classification, confidence 0.0, fail open (`SHOW_LOW_CONF`)
- EDUCATIONAL classification is never auto-skipped regardless of profile settings
- **Error handling**: Catch all Tier 3 exceptions (model load failure, inference error) → return `UNKNOWN` with confidence 0.0

### Skip Decision Engine
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

### Dual-OCR Strategy (when MediaProjection fallback is active)
- **Primary**: ML Kit Text Recognition (requires Google Play Services)
- **Fallback**: Tesseract4Android
  - Library: `io.github.nicepay:tesseract4android:4.7.0`
  - Trained data: `eng.traineddata` (fast variant, ~4.2MB) in `assets/tessdata/`, copied to `filesDir/tessdata/` on first use
  - APK impact: ~11MB total (native `.so` ~6.8MB + trained data ~4.2MB)
  - Latency: ML Kit 35-60ms vs Tesseract 180-350ms per frame
  - Mitigations: Crop capture to ROI before OCR, run in background coroutine, cache per-node results
  - Accuracy: ~5-8% higher word error rate than ML Kit; acceptable for classification
  - Language: English only for V1

### Thermal Throttling Fallback
If device is overheating, skip Tier 3 classification and rely on Tier 1 + Tier 2 only.

### Pipeline Router (`ClassificationPipeline.kt`)
```kotlin
suspend fun classify(feedItem: FeedItem, profile: UserProfile): ClassifiedItem {
    // Tier 1
    val t1 = signatureMatcher.match(feedItem)
    if (t1 != null && t1.confidence > 0.95) return t1.withSkipDecision(profile)

    // Tier 2
    val t2 = labelDetector.detect(feedItem)
    if (t2 != null) return t2.withSkipDecision(profile)

    // Tier 3
    val t3 = contentClassifier.classify(feedItem)
    return t3.withSkipDecision(profile)
}
```

## Acceptance Criteria
- Tier 1: < 5ms with 100K entries
- Tier 2: identifies all listed localised labels
- Tier 3: loads < 2s cold start, inference < 50ms on Snapdragon 7-series
- Full pipeline: < 60ms worst case
- F1: > 85% OFFICIAL_AD, > 75% INFLUENCER_PROMO
- Skip decision computed in < 1ms
- EDUCATIONAL items never auto-skipped
- Tier 3 failure gracefully falls back to UNKNOWN/SHOW_LOW_CONF

## Notes
- Open Question 5 (Model sizing): DistilBERT-tiny benchmark needed on Pixel 4a. Consider smaller alternatives if inference exceeds 50ms.
- The placeholder TFLite model file should be created in this WI. The actual trained model is produced by WI-15's ML pipeline.
```

---

### File: `InputData/RefinedWorkItems/WI-07-profile-management.md`

```markdown
# WI-07: Profile Management

## Source
- Architecture: Profile Switching (Parent → Child)
- Data Models: UserProfile, ScoringWeights
- Module 5: Child Profile Setup
- Security & Privacy: PIN Security

## Goal
Implement profile management including default profile, child profile, profile switching, PIN protection, and auto-activation scheduling.

## Context
ScrollShield supports two profile types: a default (parent) profile and a child profile. The child profile has restrictive settings. Profile switching requires PIN authentication. Child profile auto-activation can be scheduled.

## Dependencies
- **Hard**: WI-02 (UserProfile model), WI-03 (ProfileDao, UserPreferencesStore)
- **Integration**: WI-01 (DI)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/profile/ProfileManager.kt`
- `app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt`
- `app/src/main/java/com/scrollshield/profile/ProfileSwitcher.kt`

## Detailed Specification

### ProfileManager
- Create/update/delete profiles via ProfileDao
- Manage active profile (stored in UserPreferencesStore)
- Default profile created during onboarding with user's interest selections

### ChildProfileConfig
Pre-selected defaults for child profiles:
- Blocked categories: gambling, diet, crypto, explicit ads, alcohol
- Blocked classifications: `ENGAGEMENT_BAIT`, `OUTRAGE_TRIGGER`, `INFLUENCER_PROMO`
- `maskEnabled = true`, `maskDismissable = false`
- `counterEnabled = true`
- Time budget default: 15 min/app
- `pinProtected = true`

### ProfileSwitcher
- Switch between default and child profiles
- PIN authentication required to switch FROM child profile
- PIN stored as SHA-256 hash with device-specific salt (from `Settings.Secure.ANDROID_ID`)
- Lockout escalation: 3 failed attempts → 30s lockout, 5 → 5min, 10 → 30min

### Auto-Activation Schedule
- `autoActivateSchedule: Pair<LocalTime, LocalTime>?` (start, end)
- When set, automatically switch to child profile during the scheduled window
- Parent can configure via schedule picker

### Settings Timing
- Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session
- Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active

## Acceptance Criteria
- Default profile can be created with all fields
- Child profile created with correct pre-selected defaults
- PIN authentication works with SHA-256 + device salt
- Lockout escalation enforced (3 → 30s, 5 → 5min, 10 → 30min)
- Profile switch updates active profile in preferences
- Auto-activation schedule triggers profile switch at correct times
- Settings timing rules correctly enforced

## Notes
- Biometric authentication for child profile settings access is specified in the original spec. Implement using `BiometricPrompt` API as an alternative to PIN.
```

---

### File: `InputData/RefinedWorkItems/WI-08-ad-counter.md`

```markdown
# WI-08: Ad Counter Feature

## Source
- Module 3: Ad Counter (entire section)
- File Structure: `feature/counter/`

## Goal
Implement the ad counter overlay feature: session management, counting logic, overlay UI (pill + summary card), time budget nudges, and satisfaction survey.

## Context
The Ad Counter is Feature 1 — purely observational. It counts every ad served during a session and displays the count in a floating overlay pill. It never modifies the feed.

## Dependencies
- **Hard**: WI-02 (ClassifiedItem, SessionRecord), WI-05 (lifecycle events, APP_FOREGROUND/BACKGROUND), WI-06 (ClassifiedItem events)
- **Integration**: WI-03 (SessionDao for persistence), WI-07 (active profile for budget thresholds)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt`
- `app/src/main/java/com/scrollshield/feature/counter/AdCounterOverlay.kt`
- `app/src/main/java/com/scrollshield/feature/counter/SessionSummaryCard.kt`
- `app/src/main/java/com/scrollshield/feature/counter/TimeBudgetNudge.kt`
- `app/src/main/java/com/scrollshield/service/OverlayService.kt`

## Detailed Specification

### Session Management
- Starts on `APP_FOREGROUND`: load active profile, reset counter
- Ends on `APP_BACKGROUND`: write SessionRecord, show post-session satisfaction survey (1-5 stars, auto-dismiss after 10s, stored in `SessionRecord.satisfactionRating`)
- Checkpoint every 60s: write partial SessionRecord to Room with `endedNormally = false`. Overwritten on normal session end.

### Counting Logic
- Subscribes to ClassifiedItem events from both pre-scan phase and live scrolling
- Increments on `OFFICIAL_AD` or `INFLUENCER_PROMO`
- Tracks brands, categories
- Revenue estimate: `adCount * platformCPM / 1000`
  - TikTok: $10 CPM
  - Instagram: $12 CPM
  - YouTube: $15 CPM
  - CPM values configurable in Settings > Advanced
  - Note: rough estimates based on public CPM averages, not actual platform revenue
- When mask is active: displays "Ads detected: X" and "Ads skipped: Y"

### Overlay Permission
- Check `Settings.canDrawOverlays()` before rendering
- If not granted, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` with package URI
- Touch passthrough: all overlays use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`

### UI — Pill (collapsed)
- Floating `TYPE_APPLICATION_OVERLAY`, anchored top-center
- Semi-transparent dark blur
- L→R: status dot | ad count (bold white) | separator | revenue (muted) | separator | session time (muted)
- Status dot: green (0–2), amber (3–10), red (>10) with pulse animation. Thresholds configurable in Settings > Advanced.
- Monospace 11sp, height 32dp, radius 16dp
- Draggable, remembers position
- Tap to expand

### UI — Summary Card (expanded)
- Bottom-anchored, 40% height
- Shows: ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration
- Close → collapse
- Export → JSON to Downloads. JSON schema includes a `version` key for forward compatibility.

### Time Budget Nudges
Passive visual changes on the pill — no blocking:
- 80%: "5 min left" text
- 100%: flash + "Budget reached"
- 120%: pill background turns red
- Child profile at 100%: "Time's up" — mask skips all remaining content

### OverlayService
- `OverlayService.kt`: Single overlay lifecycle manager for pill, loading, skip flash, summary
- Manages WindowManager add/remove for all overlay views
- Handles overlay permission checks

## Acceptance Criteria
- Renders correctly above TikTok, Instagram, YouTube
- **Increments within 200ms of classification**
- Shows detected and skipped counts when mask active
- Correct status dot thresholds (green 0-2, amber 3-10, red >10)
- Draggable, position persists across sessions
- Valid JSON export with version key
- No touch interception on underlying app
- Budget nudges at correct thresholds (80%, 100%, 120%)
- Child hard stop at 100%
- Satisfaction survey displays on session end, auto-dismisses after 10s
- Checkpoint every 60s with `endedNormally = false`

## Notes
- The OverlayService is shared with the Scroll Mask feature (WI-09, WI-10). Define the service interface here; mask overlays plug into the same service.
```

---

### File: `InputData/RefinedWorkItems/WI-09-scroll-mask-prescan.md`

```markdown
# WI-09: Scroll Mask — Pre-Scan & Core

## Source
- Module 4: Scroll Mask (Pre-Scan Phase, Configuration, Loading Overlay UI, Child Profile Behaviour, Edge Cases)
- Architecture: "Key Insight: Pre-Scan Buffer" section
- File Structure: `feature/mask/`

## Goal
Implement the Scroll Mask pre-scan phase: loading overlay, fast-forward scan, classification, ScanMap construction, rewind, and feed mutation detection.

## Context

### Key Insight: Pre-Scan Buffer (verbatim from spec)
TikTok, Instagram Reels, and YouTube Shorts all maintain a back-stack — you can scroll forward and backward through previously loaded videos. ScrollShield exploits this by pre-scrolling the feed at session start, classifying every item, then scrolling back to the beginning. When the user starts swiping, ScrollShield already knows what's ahead and can skip flagged items with zero latency — the decision was made seconds ago.

This eliminates the timing race between classification and video rendering. There is no subliminal flash of ad content. No racing the renderer. The only cost is a few seconds of loading time at session start, masked by a branded animation.

### Core Design Principle
The user never leaves the native app. TikTok renders normally — full video, audio, native UI. ScrollShield is invisible during normal playback; the only visible interruptions are the pre-scan loading animation (~5s) and brief skip flashes.

## Dependencies
- **Hard**: WI-02 (ScanMap, ClassifiedItem), WI-05 (gesture dispatch, lifecycle events), WI-06 (classification pipeline), WI-07 (active profile for skip decisions)
- **Integration**: WI-08 (OverlayService for loading overlay)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/mask/ScrollMaskManager.kt`
- `app/src/main/java/com/scrollshield/feature/mask/PreScanController.kt`
- `app/src/main/java/com/scrollshield/feature/mask/ScanMap.kt` (runtime instance, distinct from data model)
- `app/src/main/java/com/scrollshield/feature/mask/LoadingOverlay.kt`

## Detailed Specification

### Pre-Scan Phase (session start)
When the user opens a target app with mask enabled:

1. **Show loading overlay**: Full-screen branded animation over the app. Shield icon with progress indicator. Text: "ScrollShield is preparing your feed." Blocks interaction during pre-scan.

2. **Fast-forward scan**: Call `feedInterceptionService.scrollForwardFast(bufferSize)` where `bufferSize` is configurable (default: 10). For each item:
   - Capture FeedItem via feed interception service
   - Classify via classification pipeline (computes skip decision)
   - Store in ScanMap

3. **Rewind to start**: Call `feedInterceptionService.scrollBackwardFast(bufferSize)`. **Feed mutation risk**: After rewind, verify position 0 fingerprint matches pre-scan snapshot. If platform mutated feed during pre-scan, re-scan from new position 0.

4. **Dismiss loading overlay**: User sees first video. ScrollShield is ready.

**Expected pre-scan duration**: 10 items × (200ms gesture + 60ms classification) = ~2.6s, plus ~2s rewind. Total: **~5 seconds**.

### ScanMap Runtime
- All mutable access guarded by `Mutex` for thread safety between pre-scan coroutine and user scroll handler
- `scanHead`: how far ahead pre-scan has reached
- `userHead`: where user currently is
- `skipIndices`: feed positions flagged for auto-skip
- `isExtending`: true while lookahead extension is running
- `lastValidatedHash`: feed fingerprint for re-entry validation

### Loading Overlay UI
- Full-screen overlay, dark background with subtle blur
- Center: animated shield icon (pulsing or rotating)
- Below: progress bar showing pre-scan progress (e.g., "Scanning 4/10")
- Below progress: "ScrollShield is preparing your feed"
- Below that (small, muted): "Filtering ads and unwanted content"
- For child profile: "Setting up a safe feed for [child name]"

### Configuration Constants
```
preScanBufferSize = 10
gestureIntervalMs = 200
gestureDurationMs = 100
```

### Edge Case: Back-Stack Limit
If `scrollForward` detects duplicate items (end of back-stack):
- Stop pre-scan early
- Adjust `extensionTriggerDistance` to trigger earlier

### Edge Case: User Scrolls Faster Than Extension
If user reaches edge of ScanMap before extension completes:
1. Loading overlay reappears: "ScrollShield is scanning ahead."
2. New pre-scan runs from current position
3. Loading overlay dismisses, user continues with full buffer

The user never sees unscanned content. For child profiles this is especially important — the child never sees an unvetted item.

### Child Profile Behaviour
- Mask always active, cannot be dismissed without PIN
- Pre-scan blocks: OFFICIAL_AD, INFLUENCER_PROMO, ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, plus all items in blocked categories
- At time budget 100%: mask skips ALL content. Skip flash shows "Time's up — ask a parent"
- Loading overlay uses child-friendly language and animation

### Safe Mode
When both ad counter and scroll mask are disabled, ScrollShield enters safe mode: observation-only. Accessibility service remains active for session recording and analytics but performs no gesture dispatch or overlay rendering.

### Re-Ranking (Deferred to V2)
Re-ranking feed items based on user interest scores is deferred to V2. The current architecture cannot reorder the feed without rebuilding the native app's UI.

### Low-Memory Fallback (<4GB device RAM)
- Reduce pre-scan buffer to 5 items
- Disable lookahead extension (rely on re-scan when user reaches edge)
- Priority: maintain classification accuracy over buffer size

## Acceptance Criteria
- **Loading overlay appears within 500ms of target app foregrounding**
- Pre-scan of 10 items completes in < 6 seconds
- Rewind returns to the first video accurately
- Loading overlay dismisses and user sees first video
- **Pre-computed skip decisions execute with zero additional classification latency**
- ScanMap correctly stores all pre-scanned items with skip decisions
- Feed mutation detection works (fingerprint mismatch triggers re-scan)
- When user outruns buffer, loading overlay reappears and a new pre-scan runs from current position
- User never sees unscanned content — no degraded mode exists
- Child profile: mask not dismissable without PIN
- Child profile: hard stop at time budget
- **No visible lag or stutter in native video playback during normal use**
- **Pre-scan does not cause TikTok to crash, rate-limit, or degrade feed quality**

## Notes
- Open Question 2 (Pre-scan detectability): Does TikTok detect rapid automated scrolling and penalise the account? May need to slow pre-scan to 300-400ms per item. A/B test with 200ms vs 350ms intervals on test accounts.
- Open Question 6 (Battery during pre-scan): The pre-scan is a burst of activity. Measure peak power draw and thermal impact.
```

---

### File: `InputData/RefinedWorkItems/WI-10-scroll-mask-live.md`

```markdown
# WI-10: Scroll Mask — Live Mode & Extensions

## Source
- Module 4: Scroll Mask (Live Interception Phase, Lookahead Extension, Consecutive Skip Handling, Skip Flash UI, Interest Learning)
- File Structure: `feature/mask/`

## Goal
Implement live skip execution, lookahead extension, consecutive skip handling, skip flash overlay, and interest learning.

## Context
After the pre-scan phase (WI-09), the user scrolls through the feed. This work item handles: looking up skip decisions in the ScanMap, executing skips via gesture dispatch, extending the scan buffer ahead of the user, batching consecutive skips, and updating interest vectors at session end.

## Dependencies
- **Hard**: WI-09 (ScanMap, PreScanController, ScrollMaskManager)
- **Integration**: WI-05 (gesture dispatch for skip execution), WI-08 (OverlayService for skip flash)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/mask/LookaheadExtender.kt`
- `app/src/main/java/com/scrollshield/feature/mask/SkipFlashOverlay.kt`
- `app/src/main/java/com/scrollshield/feature/mask/ConsecutiveSkipHandler.kt`
- `app/src/main/java/com/scrollshield/feature/mask/InterestLearner.kt`

## Detailed Specification

### Live Interception Phase
On each user swipe:
1. Feed interception service detects scroll and reports new feed position
2. Look up item at new position in ScanMap
3. If `skipDecision` is any `SKIP_*` variant:
   - Call `feedInterceptionService.scrollForward()` to advance past it
   - Show skip flash overlay (200ms): shield icon + "Skipped: Ad" or "Skipped: [category]"
   - Ad counter records the skipped item
   - If next item also flagged, skip again (consecutive skip handling)
4. If `SHOW` or `SHOW_LOW_CONF`: do nothing, video plays normally

### Consecutive Skip Handling
When multiple adjacent items are flagged:
- Execute skips at 300ms intervals (`consecutiveSkipIntervalMs = 300`)
- Show single combined flash: "Skipped 3 ads" rather than three separate flashes
- User sees brief fast-forward effect, lands on next clean item
- **Maximum consecutive skips**: `maxConsecutiveSkips = 5`. If >5 consecutive items would be skipped, pause and show "High ad density detected" overlay. Resume on user tap.

### Lookahead Extension
When user reaches position `scanHead - 3` (`extensionTriggerDistance = 3`):
- Trigger background extension scan (no scroll suppression, runs in background coroutine)
- Scan next `extensionSize` items (default: 5) ahead
- Add results to ScanMap
- If user catches up before extension completes, re-engage full pre-scan flow (loading overlay reappears)

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

The extension uses the same fast-forward technique but operates beyond the user's current position. It scrolls ahead, classifies, then scrolls back. 5 items × 260ms ≈ 1.3 seconds.

**Note on scroll-back visibility**: During lookahead extension, the user may briefly see the feed jump forward and back. For V1, this brief visual disruption is accepted as a known trade-off.

### Skip Flash UI
- Small overlay at bottom of screen, semi-transparent
- Shield icon + text: "Skipped: Ad" or "Skipped: Gambling" etc.
- Duration: 200ms (`skipFlashDurationMs = 200`), fades out
- For consecutive skips: "Skipped 3 ads" with count

### Edge Case: User Scrolls Backward
If user swipes down to revisit a previous video, ScanMap already has that item classified. No additional work needed.

### Interest Learning (session end)
- Runs at session end for default profile only, not child profiles
- Computes which items user watched fully vs. manually skipped
- Updates interest vector:
  ```
  interestVector = (1 - alpha) * interestVector + alpha * mean(watchedItemTopicVectors)
  ```
- Alpha values: `alpha = 0.05` for normal sessions (conservative), `alpha = 0.2` for the first 5 sessions (faster initial calibration)

### Configuration Constants
```
extensionSize = 5
extensionTriggerDistance = 3
skipFlashDurationMs = 200
consecutiveSkipIntervalMs = 300
liveGestureDurationMs = 150
maxConsecutiveSkips = 5
```

## Acceptance Criteria
- **`performSkip()` advances to next video within 500ms**
- Skip flash displays for 200ms and fades
- Consecutive skips batch correctly with combined flash
- Maximum consecutive skips (5) enforced with "High ad density" overlay
- Lookahead extension triggers at correct distance from scan head (scanHead - 3)
- Extension completes before user reaches scan head in normal scrolling
- Backward scrolling uses pre-computed decisions (no re-classification)
- Interest vector updates correctly at session end with appropriate alpha
- Alpha = 0.2 for first 5 sessions, 0.05 thereafter
- **No visible lag or stutter in native video playback during normal use**

## Notes
- The `isOwnGesture` flag (from WI-05) is critical here to distinguish user scrolls from programmatic skips — without it, a skip gesture would trigger another skip lookup creating an infinite loop.
```

---

### File: `InputData/RefinedWorkItems/WI-11-onboarding-settings.md`

```markdown
# WI-11: Onboarding & Settings UI

## Source
- Module 5: Onboarding & Settings (entire section)
- File Structure: `ui/onboarding/`, `ui/settings/`

## Goal
Implement the onboarding flow (9 screens) and the settings screen with all user-configurable options.

## Context
Onboarding guides first-time users through app selection, interest selection, blocked categories, time budgets, feature toggles, child profile setup, and permissions. Settings allows editing all choices post-onboarding. Target: complete onboarding in < 2 minutes.

## Dependencies
- **Hard**: WI-02 (UserProfile, TopicCategory), WI-03 (ProfileDao, UserPreferencesStore), WI-07 (ProfileManager, ChildProfileConfig)
- **Integration**: WI-08 (feature toggle for counter), WI-09 (feature toggle for mask)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/InterestSelectionScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/BlockedCategoryScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/TimeBudgetScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/ChildProfileSetupScreen.kt`
- `app/src/main/java/com/scrollshield/ui/settings/SettingsScreen.kt`

## Detailed Specification

### Onboarding Flow (9 steps)

1. **Welcome**: Value prop + "Get Started" button
2. **App selection**: Checkboxes for installed target apps. Detection: `PackageManager.getInstalledApplications()` filtered against target package list (`com.zhiliaoapp.musically`, `com.instagram.android`, `com.google.android.youtube`)
3. **Interest selection**: 20-topic card grid (all TopicCategory values), select 3–8
4. **Blocked categories**: Toggles for gambling, diet, crypto, political outrage, clickbait, explicit ads. All off by default.
5. **Time budget**: Slider per app, 15–120 min, default 30. Includes "Unlimited" option.
6. **Feature toggles**: Ad Counter (on by default), Scroll Mask (off by default)
7. **Child profile prompt**: "Will children use this phone?" → if yes, setup child profile (see ChildProfileSetup below)
8. **Permissions**:
   - Accessibility Service: navigate user to system settings, verify activation on return, re-prompt with explanation if not enabled
   - Overlay: `Settings.canDrawOverlays()` check, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` if needed
   - Clear explanations for each permission
9. **Done**: Confirmation + first-session tip

### Child Profile Setup
- Pre-selected blocks: gambling, diet, crypto, explicit ads, alcohol
- Pre-selected classification blocks: ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, INFLUENCER_PROMO
- Mask always on, not dismissable
- Time budget default: 15 min/app
- 4-digit PIN for parent to switch profiles

### Settings Screen
- Profile switcher at top
- Per-profile: all onboarding choices editable
- Advanced section: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size, status dot thresholds
- Data section: export CSV/JSON, delete all data
- Child profile management: edit, reset, change PIN
- **Authentication**: Access to child profile settings requires biometric or PIN authentication

### Settings Timing Rules
- Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session
- Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active

## Acceptance Criteria
- Onboarding completes in < 2 min
- All 20 topic categories displayed in grid
- Preferences persist across app restarts
- Child profile PIN works
- Settings changes apply at correct timing (next session vs. immediate)
- Biometric/PIN required for child profile settings
- Delete all data wipes everything (database + preferences)
- App selection only shows installed target apps

## Notes
- All UI uses Jetpack Compose per the tech stack.
- The SQLCipher toggle (optional database encryption) belongs in Settings > Advanced.
```

---

### File: `InputData/RefinedWorkItems/WI-12-signature-sync.md`

```markdown
# WI-12: Signature Sync & Local Learning

## Source
- Module 6: Signature Database & Sync (entire section)
- File Structure: `data/sync/`

## Goal
Implement signature sync with remote server, local learning (generating signatures from detected ads), and expiry cleanup.

## Context
The ad signature database powers Tier 1 matching. It can be populated via server sync and via local detection. Sync is optional — the app is fully functional without it. WiFi-only by default.

## Dependencies
- **Hard**: WI-02 (AdSignature model), WI-03 (SignatureDao), WI-06 (Tier 1 uses signatures, local learning generates them)
- **Integration**: WI-04 (SimHash for generating local signatures)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/data/sync/SignatureSyncWorker.kt`
- `app/src/main/java/com/scrollshield/data/sync/SignatureApiClient.kt`

## Detailed Specification

### Sync
- Endpoint: `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- HTTPS REST, delta sync on `last_sync_timestamp`
- Schedule: every 24h on WiFi, or manual trigger from Settings
- Payload: Gzipped JSON
- Conflict resolution: prefer `synced` source over `local_detection` when signatures overlap
- Database migration: Room auto-migrations with server schema version check
- Fully functional without sync — app works entirely offline

### SignatureSyncWorker
- WorkManager periodic work request (24h)
- Constraint: `NetworkType.UNMETERED` (WiFi-only default)
- On success: update `last_sync_timestamp` in preferences
- On failure: retry with exponential backoff

### SignatureApiClient
- Retrofit/OkHttp-based HTTP client
- `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- Parse gzipped JSON response into `List<AdSignature>`
- Handle errors: network failure, malformed response, server error

### Local Learning
- Ads detected by Tier 2 or Tier 3 that are not in the signature cache → generate `AdSignature`:
  - `source = "local_detection"`
  - `simHash` computed via SimHash utility
  - `confidence` from the classification result
  - `expires` = current time + 30 days
- Stored locally, used in subsequent Tier 1 lookups
- Not shared unless user opts in (future feature)

### Expiry Cleanup
- Nightly WorkManager task removes expired signatures (`expires < currentTime`)
- Runs as a one-time work request rescheduled daily

## Acceptance Criteria
- Lookup < 5ms for 100K entries (Tier 1 performance)
- Delta sync adds/removes signatures correctly
- **WiFi-only default** (uses `NetworkType.UNMETERED` constraint)
- Local detections stored and used in Tier 1
- Expired signatures cleaned up nightly
- **Database < 50MB** (target: 50K–100K entries)
- Conflict resolution prefers synced over local_detection
- App fully functional without any sync

## Notes
- ProGuard rules should strip HTTP client classes outside this module (enforced in WI-01/WI-14).
```

---

### File: `InputData/RefinedWorkItems/WI-13-session-analytics.md`

```markdown
# WI-13: Session Analytics & Reporting UI

## Source
- Module 7: Session Analytics & Reporting (entire section)
- File Structure: `ui/reports/`

## Goal
Implement weekly reports, monthly aggregates, child activity reports, data retention policy, and the reporting UI.

## Context
Session recording is done by the Ad Counter (WI-08) during sessions. This work item adds post-session analytics: weekly reports, monthly aggregates, child activity reports, data export, and data retention cleanup.

## Dependencies
- **Hard**: WI-02 (SessionRecord), WI-03 (SessionDao), WI-08 (sessions are recorded here)
- **Integration**: WI-07 (profiles for child activity filtering)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/ui/reports/ReportScreen.kt`
- `app/src/main/java/com/scrollshield/ui/reports/WeeklyReportCard.kt`
- `app/src/main/java/com/scrollshield/ui/reports/ChildActivityReport.kt`

## Detailed Specification

### Weekly Report
- Generated via WorkManager 7-day PeriodicWorkRequest, anchored to Sunday midnight in device timezone
- Contents: total time (split by profile), ads detected and skipped, top 5 targeting brands, ad-to-content ratio trend, satisfaction average
- In-app display + JSON export
- Triggers notification: "Your weekly ScrollShield report is ready"

### Monthly Aggregates
- Computed at month end: total sessions, total duration, total ads detected, total ads skipped, average satisfaction, per-app breakdown, top 10 ad brands
- Stored as summary records for long-term trend analysis

### Child Activity Report
- Parent-facing: child sessions only
- Time per app, ads blocked, categories encountered, budget compliance
- On-demand generation from Reports screen
- Exportable separately

### Data Retention
- Raw sessions: 90 days → then delete (use `SessionDao.deleteOlderThan()`)
- Monthly aggregates retained indefinitely
- Cleanup via scheduled WorkManager task

### Report Screen UI
- Performance: `@RawQuery` with aggregation SQL, index on `(profileId, startTime)` — render < 1s with 90 days data
- Export: CSV and JSON formats
- Valid export format with appropriate headers/schema

## Acceptance Criteria
- Session written on every session end
- Checkpoint survives force-kill
- Weekly report generates correctly on schedule
- Weekly report notification delivered on schedule
- Child report shows child sessions only
- Valid CSV/JSON export
- Report screen renders < 1s with 90 days of data
- Monthly aggregates computed correctly
- Data retention: raw sessions deleted after 90 days, aggregates kept

## Notes
- Open Question 10 (Feed algorithm adaptation): Platforms may adapt feed algorithms if they detect consistent skipping patterns. The reporting UI should track ad frequency per session over time so users can monitor if ad density is increasing.
```

---

### File: `InputData/RefinedWorkItems/WI-14-error-handling.md`

```markdown
# WI-14: Error Handling & Diagnostics

## Source
- Error Handling & Recovery section (entire)
- Security & Privacy section
- File Structure: `error/`

## Goal
Implement centralized error handling, recovery strategies, and structured diagnostic logging.

## Context
ScrollShield must handle: accessibility service disconnection, TFLite model load failure, database corruption, gesture dispatch failure, and pre-scan timeout. Each has a specific recovery strategy defined in the spec.

## Dependencies
- **Hard**: WI-05 (accessibility service), WI-06 (TFLite model), WI-03 (database), WI-09 (pre-scan)
- **Integration**: WI-08 (pill overlay for status indicators)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/error/ErrorRecoveryManager.kt`
- `app/src/main/java/com/scrollshield/error/DiagnosticLogger.kt`

## Detailed Specification

### Accessibility Service Disconnection
- Android may kill the accessibility service under memory pressure
- On disconnect: show persistent notification "ScrollShield protection paused"
- On reconnect: restart service, validate ScanMap if session was active

### TFLite Model Load Failure
- If model file is corrupted or missing: fall back to Tier 1 + Tier 2 only
- Log diagnostic event
- Show subtle indicator on pill overlay

### Database Corruption
- Room database corruption: delete and recreate database
- DataStore preferences survive database reset (stored separately)
- Log corruption event for diagnostics

### Gesture Dispatch Failure
- Track consecutive gesture failures
- After 3 consecutive failures: disable scroll mask for remainder of session
- Show notification: "Scroll protection paused — feed interaction failed"

### Pre-Scan Timeout
- If pre-scan does not complete within 15 seconds: abort pre-scan
- Fall back to live classification mode (classify each item as user scrolls)
- Show brief notification on loading overlay: "Pre-scan timed out — running in live mode"

### DiagnosticLogger
- Structured logging for debugging
- Log events: service connect/disconnect, model load/fail, gesture success/fail, pre-scan start/complete/timeout, database operations
- Do not log user content (captions, creator names) — only metadata

### Security Enforcement
- Verify ProGuard rules strip HTTP client classes outside signature sync module
- Verify export contains no raw content (captions, creator names) — only aggregated statistics
- Verify zero network calls during classification, session recording, and reporting flows

## Acceptance Criteria
- Accessibility service disconnection shows notification
- Service reconnection validates ScanMap
- TFLite failure gracefully degrades to Tier 1+2
- Database corruption detected and recovered
- 3 consecutive gesture failures disables mask for session
- Pre-scan timeout at 15s with fallback to live mode
- Diagnostic logs contain no user content
- ProGuard rules correctly strip HTTP classes outside sync module

## Notes
- The thermal throttling fallback (skip Tier 3 if device overheating) is implemented in WI-06 but the temperature monitoring can be wired through this error handling infrastructure.
```

---

### File: `InputData/RefinedWorkItems/WI-15-testing-ml-pipeline.md`

```markdown
# WI-15: Testing Infrastructure & ML Pipeline

## Source
- Testing Strategy section (entire)
- File Structure: `ml/`, `app/src/test/`, `app/src/androidTest/`
- Memory Budget section
- Performance Benchmarks

## Goal
Set up the testing infrastructure (unit tests, integration tests, performance benchmarks, privacy tests) and the ML training/export pipeline.

## Context
This work item creates the test harnesses, test data, and the Python ML pipeline for training and exporting the TFLite classifier model.

## Dependencies
- **Hard**: All other work items (this validates them)
- **Integration**: WI-06 (model is consumed by Tier 3)

## Files to Create / Modify

- `app/src/test/` — unit test files
- `app/src/androidTest/` — integration test files
- `ml/train_classifier.py`
- `ml/export_tflite.py`
- `ml/dataset/` — synthetic test data
- `ml/eval/` — evaluation scripts

## Detailed Specification

### Unit Tests
- Classification: each tier with mock inputs
- Skip decision engine: all rules, all profile types
- ScanMap: position tracking, lookahead triggers, edge cases
- Ad counter: counting, revenue calculation, budget thresholds
- Pre-scan controller: buffer construction, rewind accuracy
- Consecutive skip handler: batching logic
- Accessibility service mock: Robolectric `ShadowAccessibilityService`

### Integration Tests
- Full pre-scan cycle: fast-forward → classify → rewind → loading overlay dismiss
- Live skip: user swipe → ScanMap lookup → skip execution → flash
- Lookahead extension: trigger at correct distance, complete before user catches up
- Buffer exhaustion: user outruns buffer → loading overlay → re-scan → continue
- Child profile: all blocked types skipped, time budget enforced
- Session recording on background and force-kill

### App Update Regression
- Saved accessibility tree snapshot suite for each target app version
- Run against `compat/` extractors to detect breakage on app updates

### Test Data
- Synthetic feed: 200 items — 60% organic, 20% official ads, 10% influencer promos, 10% engagement bait
- Known classifications for accuracy tests
- Child-safety set: 50 items with gambling, diet, outrage — verify child profile blocks all

### Performance Benchmarks
| Benchmark | Target |
|---|---|
| Pre-scan (10 items) | < 6 seconds |
| Rewind (10 items) | < 4 seconds |
| Classification pipeline | < 60ms per item |
| Live skip (ScanMap lookup + gesture) | < 500ms |
| Skip flash render | exactly 200ms |
| Signature lookup (100K entries) | < 5ms |
| Cosine similarity | < 1ms per item |
| Counter overlay | zero frame rate impact |

### Performance Regression
- CI benchmark suite: fail build if median classification latency exceeds 80ms

### Privacy Tests
- Verify zero network calls during classification, session recording, and reporting flows
- Only signature sync and data export may make network calls

### Memory Budget Validation
| Component | Estimated Usage |
|---|---|
| TFLite model (loaded) | ~15MB |
| TFLite interpreter | ~10MB |
| SimHash index (100K entries) | ~12MB |
| ScanMap (10-item buffer) | <1MB |
| Room database connection | ~5MB |
| Overlay rendering (Compose) | ~15MB |
| Accessibility tree cache | ~8MB |
| Coroutines + Flow buffers | ~10MB |
| **Headroom** | **~74MB** |
| **Total** | **~150MB peak** |

Test: verify peak memory stays under 150MB during pre-scan + live mode.

### ML Pipeline

#### `train_classifier.py`
- Train DistilBERT-tiny (4L/128H) for 7-class classification + 20-dim topic vector
- Input: social media captions with labels
- Output: PyTorch model checkpoint
- Framework: PyTorch + HuggingFace Transformers

#### `export_tflite.py`
- Convert PyTorch model → TFLite with float16 quantization
- Output: `scrollshield_classifier.tflite` (~15MB)
- Validate: inference < 50ms on reference device
- Input tensor: `int32[1][128]`
- Output tensors: `float32[1][7]` (classifications), `float32[1][20]` (topic vector)

## Acceptance Criteria
- All unit tests pass
- Integration tests cover all critical flows
- Performance benchmarks meet all targets
- Privacy tests verify zero unauthorized network calls
- ML pipeline produces valid TFLite model
- TFLite model inference < 50ms
- Peak memory < 150MB

## Notes
- Instrumented tests require a real device or emulator with target apps installed.
- The ML training pipeline requires Python with PyTorch and HuggingFace — separate from the Android build.
```

---

## Build Order

```
WI-01 (Scaffolding)
  └→ WI-02 (Data Models)
       ├→ WI-03 (Database/DAOs)
       │    └→ WI-07 (Profiles)
       │         └→ WI-11 (Onboarding/Settings)
       └→ WI-04 (Utilities)
            └→ WI-05 (Feed Interception)
                 └→ WI-06 (Classification)
                      ├→ WI-08 (Ad Counter)
                      ├→ WI-09 (Mask Pre-Scan)
                      │    └→ WI-10 (Mask Live Mode)
                      └→ WI-12 (Signature Sync)
WI-13 (Analytics) — after WI-08
WI-14 (Error Handling) — after WI-05, WI-06, WI-09
WI-15 (Testing/ML) — after all others
```

### Parallel opportunities
- WI-03 and WI-04 can run in parallel (both depend only on WI-02)
- WI-07 and WI-05 can run in parallel (WI-07 needs WI-03, WI-05 needs WI-04)
- WI-08 and WI-09 can run in parallel (both need WI-06)
- WI-11, WI-12, WI-13 can run in parallel after their respective dependencies

### Milestone Mapping
- **MVP** (original steps 1-5): WI-01, WI-02, WI-03, WI-04, WI-05, WI-06 (Tier 2 + Skip Decision), WI-08 — Feed interception + label-based detection + ad counter. Shippable standalone.
- **V1** (original steps 1-9): MVP + WI-06 (full pipeline with Tier 1 + Tier 3), WI-07, WI-09, WI-10, WI-11 — Both features with pre-scan buffer and child profiles.
- **V1.1** (original steps 10-12): V1 + WI-12, WI-13, WI-14, WI-15 — Enhanced classification, signatures, analytics, integration testing.
