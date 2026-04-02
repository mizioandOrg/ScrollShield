# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

Implement WI-03: Room database, all DAOs, TypeConverters, and DataStore preferences store for the ScrollShield Android project.

All files must be created in the project at `/home/devuser/add-gh-and-nano-docker-dev-worktree`.

Specifically:
- Create `ScrollShieldDatabase` — Room database class annotated with all 3 entities and all required TypeConverters
- Create `SessionDao`, `SignatureDao`, `ProfileDao` — Room DAOs with the exact methods specified in the work item
- Create any missing TypeConverters (`StringListConverter`, `ClassificationIntMapConverter`) in a new file `app/src/main/java/com/scrollshield/data/db/TypeConverters.kt`
- Create `UserPreferencesStore` backed by DataStore (Preferences API) with `EncryptedSharedPreferences` for sensitive fields
- Update `DatabaseModule.kt` to replace the placeholder `Any` provider with a real `ScrollShieldDatabase` Hilt singleton, with optional SQLCipher support wired in

### TypeConverter reconciliation
UserProfile.kt already defines these converters (do NOT duplicate them, just reference them):
- `TopicCategorySetConverter`, `ClassificationSetConverter`, `StringIntMapConverter`, `FloatListConverter`, `ScoringWeightsConverter`, `LocalTimeScheduleConverter`

The WI-03 spec uses `LocalTimePairConverter` in `@TypeConverters` — use `LocalTimeScheduleConverter` (the actual existing class) instead, OR create a `LocalTimePairConverter` typealias/subclass pointing to the same logic.

New converters to create: `StringListConverter`, `ClassificationIntMapConverter`.

### SessionDao index
Add a Room index on `(profileId, startTime)` to `SessionRecord` entity — but do NOT modify `SessionRecord.kt` directly. Instead, declare the index inside the `@Database` `indices` parameter or via a migration. Prefer adding `@Index` inside `ScrollShieldDatabase` using `@Entity` override — actually: add the index in `SessionDao` or document it as a known limitation if Room doesn't support external index declarations. The recommended approach: add the compound index annotation directly inside `ScrollShieldDatabase` `entities` list via a wrapper or add it to the existing `SessionRecord` entity annotation. Since modifying model files is not allowed, note this limitation clearly in a code comment and declare the index in `ScrollShieldDatabase` using an `AutoMigrationSpec` or `@ForeignKeys` — document the approach chosen.

**Clarification**: The index requirement is a performance concern for WI-13. For this WI, add the index by declaring it in a `@Entity`-annotated wrapper or by documenting that `SessionRecord.kt` must eventually have `@Index(value = ["profileId", "startTime"])` added (which is WI-02 territory). Do not block on this — proceed with a comment.

## Context Files

All paths are relative to `/home/devuser/add-gh-and-nano-docker-dev-worktree`:

- `app/src/main/java/com/scrollshield/data/model/AdSignature.kt`
- `app/src/main/java/com/scrollshield/data/model/SessionRecord.kt`
- `app/src/main/java/com/scrollshield/data/model/UserProfile.kt`
- `app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt`
- `app/src/main/java/com/scrollshield/di/DatabaseModule.kt`
- `app/build.gradle.kts`
- `work-items/WI-03-database-daos-preferences.md`

## Target Files (to modify or create)

All paths relative to `/home/devuser/add-gh-and-nano-docker-dev-worktree`:

- `app/src/main/java/com/scrollshield/data/db/ScrollShieldDatabase.kt` (create)
- `app/src/main/java/com/scrollshield/data/db/SessionDao.kt` (create)
- `app/src/main/java/com/scrollshield/data/db/SignatureDao.kt` (create)
- `app/src/main/java/com/scrollshield/data/db/ProfileDao.kt` (create)
- `app/src/main/java/com/scrollshield/data/db/TypeConverters.kt` (create — for StringListConverter, ClassificationIntMapConverter)
- `app/src/main/java/com/scrollshield/data/preferences/UserPreferencesStore.kt` (create)
- `app/src/main/java/com/scrollshield/di/DatabaseModule.kt` (modify — replace placeholder with real DB)

## Rules & Constraints

- Do NOT modify any file under `app/src/main/java/com/scrollshield/data/model/` — those are WI-02 outputs and are read-only
- Do NOT modify `app/build.gradle.kts` — all required dependencies (Room 2.6.1, DataStore 1.0.0, Hilt 2.51.1, Gson) are already present
- Do NOT duplicate TypeConverter classes already defined in `UserProfile.kt`; import them instead
- All new files must use package `com.scrollshield.data.db` or `com.scrollshield.data.preferences` as appropriate
- SQLCipher support must default to OFF; wire the factory conditionally (e.g., via a boolean parameter or a build config flag)
- `UserPreferencesStore` must use `androidx.datastore.preferences.core.*` (Preferences DataStore, not Proto DataStore)
- Use `androidx.security.crypto.EncryptedSharedPreferences` for sensitive fields (PIN hash)
- All DAO methods that are not `Flow`-returning must be `suspend` functions
- The `@Database` `@TypeConverters` annotation must list the actual class names that exist in the compiled code

## Review Criteria

1. `ScrollShieldDatabase` is annotated with `@Database` listing all 3 entities (`SessionRecord`, `AdSignature`, `UserProfile`) and references all required TypeConverter classes by their exact compiled names (no phantom classes)
2. `SessionDao` implements all 5 methods: `upsert`, `getSessionsByProfile` (Flow), `getSessionsSince`, `getSessionsByProfileSince`, `deleteOlderThan` — with correct SQL table name matching the `@Entity` `tableName` on `SessionRecord`
3. `SignatureDao` implements all 6 methods including `getActiveVisualHashes` which filters for `visualHash IS NOT NULL AND expires > :now`
4. `ProfileDao` implements `upsert`, `getById`, `getAllProfiles` (Flow), `delete` — SQL references match the `UserProfile` entity table name
5. `StringListConverter` and `ClassificationIntMapConverter` are correctly implemented with Gson round-trip, and no existing converters from `UserProfile.kt` are duplicated
6. `UserPreferencesStore` defines Preferences DataStore keys for: `activeProfileId`, `onboardingCompleted`, `mediaProjectionGranted`, `visualClassificationEnabled` (default: true), and at least one advanced settings key; uses `EncryptedSharedPreferences` for the PIN hash
7. `DatabaseModule.kt` provides a real `ScrollShieldDatabase` singleton via `Room.databaseBuilder` (or SQLCipher variant) with Hilt `@Provides @Singleton`, replacing the placeholder `Any` return type
8. SQLCipher is wired as an optional path (off by default) — the database builder includes a commented or conditional branch for the SQLCipher `SupportSQLiteOpenHelper.Factory`
9. All imports are resolvable — no references to non-existent classes; TypeConverter names in `@TypeConverters` annotation match actual class names
10. `autoMigrations = []` is present in `@Database` to establish the auto-migration infrastructure baseline (version = 1)

## Implementation Instructions

```
cd /home/devuser/add-gh-and-nano-docker-dev-worktree
./gradlew :app:compileDebugKotlin --no-daemon 2>&1 | tail -60
```
