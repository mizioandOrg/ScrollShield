# Approved Plan — WI-02 Data Models & Enums
# Iteration 3 — Score: 10/10

## FeedItem.kt

```kotlin
package com.scrollshield.data.model

import android.graphics.Bitmap
import android.graphics.Rect

data class FeedItem(
    val id: String,                     // SHA-256 of (captionText + creatorName + app + feedPosition)
    val timestamp: Long,                // Unix epoch ms
    val app: String,                    // Package name of source app
    val creatorName: String,
    val captionText: String,
    val hashtags: List<String>,
    val labelText: String?,             // "Sponsored" / "Ad" label if found
    val screenRegion: Rect,
    /**
     * Debug only — stripped in release builds (ProGuard/R8), max 4 KB.
     */
    val rawNodeDump: String,
    val feedPosition: Int,              // Position in the feed back-stack (0 = first loaded)
    val accessibilityNodeId: Long?,     // Accessibility node identifier for re-verification
    val detectedDurationMs: Long?,      // Duration of content if detectable from accessibility tree
    val screenCapture: Bitmap?,         // Screen capture from MediaProjection, null if unavailable
)
```

## ClassifiedItem.kt

```kotlin
package com.scrollshield.data.model

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

    companion object {
        fun fromIndex(i: Int) = entries.first { it.index == i }
    }
}

data class ClassifiedItem(
    val feedItem: FeedItem,
    val classification: Classification,
    val confidence: Float,              // 0.0 to 1.0
    val topicVector: FloatArray,        // 20-dimensional, maps to TopicCategory entries
    val topicCategory: TopicCategory,   // Dominant topic from topicVector argmax
    /** Which tier classified it: 0 = text fast-path, 1 = visual, 2 = deep text */
    val tier: Int,
    val latencyMs: Long,
    val classifiedAt: Long,             // Unix epoch ms when classification was performed
    val skipDecision: SkipDecision      // Pre-computed skip/show decision
)
```

## UserProfile.kt

```kotlin
package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalTime

@Entity
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val isChildProfile: Boolean,
    val interestVector: FloatArray,
    val blockedCategories: Set<TopicCategory>,
    val blockedClassifications: Set<Classification>,
    val timeBudgets: Map<String, Int>,
    val maskEnabled: Boolean,
    val counterEnabled: Boolean,
    val maskDismissable: Boolean,
    val pinProtected: Boolean,
    val parentPinHash: String?,
    val satisfactionHistory: List<Float>,
    val scoringWeights: ScoringWeights,
    val createdAt: Long,
    val updatedAt: Long,
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
    fun fromSet(categories: Set<TopicCategory>): String =
        categories.joinToString(",") { it.name }

    @TypeConverter
    fun toSet(value: String): Set<TopicCategory> =
        if (value.isBlank()) emptySet()
        else value.split(",").map { TopicCategory.valueOf(it) }.toSet()
}

class ClassificationSetConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromSet(values: Set<Classification>): String =
        gson.toJson(values.map { it.name })

    @TypeConverter
    fun toSet(value: String): Set<Classification> {
        val type = object : TypeToken<List<String>>() {}.type
        val list: List<String> = gson.fromJson(value, type)
        return list.map { Classification.valueOf(it) }.toSet()
    }
}

class StringIntMapConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<String, Int>): String = gson.toJson(map)

    @TypeConverter
    fun toMap(value: String): Map<String, Int> {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(value, type)
    }
}

class FloatListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<Float>): String = gson.toJson(list)

    @TypeConverter
    fun toList(value: String): List<Float> {
        val type = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, type)
    }
}

class ScoringWeightsConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromScoringWeights(weights: ScoringWeights): String = gson.toJson(weights)

    @TypeConverter
    fun toScoringWeights(value: String): ScoringWeights =
        gson.fromJson(value, ScoringWeights::class.java)
}

class LocalTimeScheduleConverter {
    @TypeConverter
    fun fromSchedule(schedule: Pair<LocalTime, LocalTime>?): String? =
        schedule?.let { "${it.first}|${it.second}" }

    @TypeConverter
    fun toSchedule(value: String?): Pair<LocalTime, LocalTime>? {
        if (value == null) return null
        val parts = value.split("|")
        return Pair(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]))
    }
}
```

## SessionRecord.kt

```kotlin
package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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

## AdSignature.kt

```kotlin
package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ad_signatures")
data class AdSignature(
    @PrimaryKey val id: String,
    val advertiser: String,
    val category: String,
    val captionHash: String,
    val simHash: Long,          // 64-bit SimHash for fast Tier 1 matching
    val confidence: Float,      // 0.0-1.0
    val firstSeen: Long,
    val expires: Long,
    val source: String,
    val locale: String?,
    val visualHash: String?     // Perceptual hash of ad screenshot for visual signature matching
)
```

## ScanMap.kt

```kotlin
package com.scrollshield.data.model

/**
 * In-memory map of classified feed items for the current scroll session.
 *
 * Lifecycle rules:
 * - **Target-to-target**: Discard ScanMap, finalize session, start new pre-scan.
 * - **Return within 60s**: Retain ScanMap, validate via [lastValidatedHash].
 *   Match = reuse existing map; mismatch = re-scan.
 * - **Return after 60s**: Discard ScanMap and perform a fresh pre-scan.
 */
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
