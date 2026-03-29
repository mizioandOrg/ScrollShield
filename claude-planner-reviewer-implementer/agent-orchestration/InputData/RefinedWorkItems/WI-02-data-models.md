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
    val confidence: Float,         // 0.0-1.0
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
