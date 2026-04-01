# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet  # optional: haiku, sonnet, or opus (default: sonnet)
Implement: yes  # optional: yes or no (default: yes) — set to no to stop after plan approval

## Task Description

Implement WI-02: Data Models & Enums for the ScrollShield Android app.

Create all 6 Kotlin data model files under `app/src/main/java/com/scrollshield/data/model/`:
- `FeedItem.kt` — 13-field data class
- `ClassifiedItem.kt` — 9-field data class containing Classification, SkipDecision, TopicCategory enums
- `UserProfile.kt` — data class with Room @Entity, ScoringWeights, TopicCategorySetConverter, and all needed Room TypeConverters
- `SessionRecord.kt` — Room @Entity data class
- `AdSignature.kt` — Room @Entity data class
- `ScanMap.kt` — data class with KDoc lifecycle rules

All classes must be implemented verbatim from the WI-02 spec (see Context Files).

## Context Files

- work-items/WI-02-data-models.md
- app/build.gradle.kts

## Target Files (to modify)

- app/src/main/java/com/scrollshield/data/model/FeedItem.kt
- app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- app/src/main/java/com/scrollshield/data/model/SessionRecord.kt
- app/src/main/java/com/scrollshield/data/model/AdSignature.kt
- app/src/main/java/com/scrollshield/data/model/ScanMap.kt

## Rules & Constraints

- Implement all classes verbatim from the spec in WI-02-data-models.md — do not add, remove, or rename fields
- Do not modify any existing files (build.gradle.kts, di/, accessibility/, app/, etc.)
- All files must use package `com.scrollshield.data.model`
- Room annotations (@Entity, @PrimaryKey, @TypeConverter) must be applied exactly as specified
- `rawNodeDump` must include a KDoc comment marking it as debug-only with max 4KB cap
- The `ScanMap` class must include KDoc lifecycle rules as specified (target-to-target, return within 60s, return after 60s)
- Do not introduce any imports not needed by the spec

## Review Criteria

1. All 6 model files are created with the correct package declaration `package com.scrollshield.data.model`
2. `FeedItem` has exactly 13 fields matching the spec, including `screenCapture: Bitmap?` and `detectedDurationMs: Long?`
3. `rawNodeDump` has a KDoc or inline comment marking it as debug-only with a max 4KB cap note
4. `ClassifiedItem` has all 9 fields; the `tier` field's KDoc documents tiers 0=text fast-path, 1=visual, 2=deep text
5. All three enums (`Classification`, `SkipDecision`, `TopicCategory`) contain every entry from the spec with correct values
6. `TopicCategory` companion object implements `fromIndex(i: Int)` covering all 20 entries (indices 0–19)
7. `UserProfile` is annotated `@Entity` and `UserProfile.kt` contains `TopicCategorySetConverter` plus TypeConverters for `Set<Classification>`, `Map<String, Int>`, `List<Float>`, `ScoringWeights`, and `Pair<LocalTime, LocalTime>?`
8. `ScoringWeights` default values are exactly `interest=0.35f`, `wellbeing=0.25f`, `novelty=0.15f`, `manipulation=0.25f`
9. `AdSignature` is annotated `@Entity(tableName = "ad_signatures")` and includes the `visualHash: String?` field
10. `ScanMap` contains KDoc lifecycle rules for all three scenarios; `SessionRecord` is annotated `@Entity(tableName = "sessions")`

## Implementation Instructions

```
echo "WI-02 data model files created. Compile verification requires Android SDK (run ./gradlew assembleDebug in the project root)."
```
