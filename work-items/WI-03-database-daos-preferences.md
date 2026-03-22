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

    @Query("SELECT visualHash FROM ad_signatures WHERE visualHash IS NOT NULL AND expires > :now")
    suspend fun getActiveVisualHashes(now: Long): List<String>
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
- Stores: active profile ID, onboarding completed flag, feature toggles, advanced settings (CPM overrides, buffer sizes, status dot thresholds), **mediaProjectionGranted flag**, **visualClassificationEnabled flag** (default: true)

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
- `mediaProjectionGranted` and `visualClassificationEnabled` preferences persist across app restarts
- `getActiveVisualHashes()` returns only non-null, non-expired visual hashes

## Notes
- Data retention policy (90-day raw sessions → monthly aggregates) is implemented in WI-13, not here. This WI provides the DAO methods (`deleteOlderThan`) that WI-13 will call.
