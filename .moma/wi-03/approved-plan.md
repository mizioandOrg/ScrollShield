# Approved Plan — WI-03 Database, DAOs & Preferences Store
# Approved at Iteration 3 — Score: 10/10

## File 1: `app/src/main/java/com/scrollshield/data/db/TypeConverters.kt` (create)

```kotlin
package com.scrollshield.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scrollshield.data.model.Classification

/**
 * Converts List<String> to/from JSON string.
 * Used for SessionRecord.adBrands, SessionRecord.adCategories.
 */
class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}

/**
 * Converts Map<Classification, Int> to/from JSON string.
 * Used for SessionRecord.classificationCounts.
 */
class ClassificationIntMapConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<Classification, Int>): String {
        val stringKeyMap = map.entries.associate { (k, v) -> k.name to v }
        return gson.toJson(stringKeyMap)
    }

    @TypeConverter
    fun toMap(value: String): Map<Classification, Int> {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        val stringKeyMap: Map<String, Int> = gson.fromJson(value, type)
        return stringKeyMap.entries.associate { (k, v) -> Classification.valueOf(k) to v }
    }
}

/**
 * Converts FloatArray to/from JSON string.
 * Used for UserProfile.interestVector.
 */
class FloatArrayConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String = gson.toJson(array.toList())

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        val type = object : TypeToken<List<Float>>() {}.type
        val list: List<Float> = gson.fromJson(value, type)
        return list.toFloatArray()
    }
}
```

## File 2: `app/src/main/java/com/scrollshield/data/db/ScrollShieldDatabase.kt` (create)

```kotlin
package com.scrollshield.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scrollshield.data.model.AdSignature
import com.scrollshield.data.model.ClassificationSetConverter
import com.scrollshield.data.model.FloatListConverter
import com.scrollshield.data.model.LocalTimeScheduleConverter
import com.scrollshield.data.model.ScoringWeightsConverter
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.data.model.StringIntMapConverter
import com.scrollshield.data.model.TopicCategorySetConverter
import com.scrollshield.data.model.UserProfile

// NOTE: An index on (profileId, startTime) for performance would need to be
// declared inside @Entity on SessionRecord. That file cannot be modified in this WI.
// Add `indices = [Index(value = ["profileId", "startTime"])]` to SessionRecord in WI-04.

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
    FloatArrayConverter::class,
    ScoringWeightsConverter::class,
    LocalTimeScheduleConverter::class
)
abstract class ScrollShieldDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun signatureDao(): SignatureDao
    abstract fun profileDao(): ProfileDao
}
```

## File 3: `app/src/main/java/com/scrollshield/data/db/SessionDao.kt` (create)

```kotlin
package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.SessionRecord
import kotlinx.coroutines.flow.Flow

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

## File 4: `app/src/main/java/com/scrollshield/data/db/SignatureDao.kt` (create)

```kotlin
package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.AdSignature

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

## File 5: `app/src/main/java/com/scrollshield/data/db/ProfileDao.kt` (create)

```kotlin
package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

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

## File 6: `app/src/main/java/com/scrollshield/data/preferences/UserPreferencesStore.kt` (create)

```kotlin
package com.scrollshield.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_MEDIA_PROJECTION_GRANTED = booleanPreferencesKey("media_projection_granted")
        val KEY_VISUAL_CLASSIFICATION_ENABLED = booleanPreferencesKey("visual_classification_enabled")
        val KEY_CPM_OVERRIDE_USD = floatPreferencesKey("cpm_override_usd")
        val KEY_PRESCAN_BUFFER_SIZE = intPreferencesKey("prescan_buffer_size")
        val KEY_STATUS_DOT_THRESHOLD = floatPreferencesKey("status_dot_threshold")
    }

    val activeProfileId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_PROFILE_ID] }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }
    val mediaProjectionGranted: Flow<Boolean> = context.dataStore.data.map { it[KEY_MEDIA_PROJECTION_GRANTED] ?: false }
    val visualClassificationEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_VISUAL_CLASSIFICATION_ENABLED] ?: true }
    val cpmOverrideUsd: Flow<Float> = context.dataStore.data.map { it[KEY_CPM_OVERRIDE_USD] ?: 2.5f }
    val preScanBufferSize: Flow<Int> = context.dataStore.data.map { it[KEY_PRESCAN_BUFFER_SIZE] ?: 10 }
    val statusDotThreshold: Flow<Float> = context.dataStore.data.map { it[KEY_STATUS_DOT_THRESHOLD] ?: 0.7f }

    suspend fun setActiveProfileId(id: String) { context.dataStore.edit { it[KEY_ACTIVE_PROFILE_ID] = id } }
    suspend fun setOnboardingCompleted(completed: Boolean) { context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed } }
    suspend fun setMediaProjectionGranted(granted: Boolean) { context.dataStore.edit { it[KEY_MEDIA_PROJECTION_GRANTED] = granted } }
    suspend fun setVisualClassificationEnabled(enabled: Boolean) { context.dataStore.edit { it[KEY_VISUAL_CLASSIFICATION_ENABLED] = enabled } }
    suspend fun setCpmOverrideUsd(cpm: Float) { context.dataStore.edit { it[KEY_CPM_OVERRIDE_USD] = cpm } }
    suspend fun setPreScanBufferSize(size: Int) { context.dataStore.edit { it[KEY_PRESCAN_BUFFER_SIZE] = size } }
    suspend fun setStatusDotThreshold(threshold: Float) { context.dataStore.edit { it[KEY_STATUS_DOT_THRESHOLD] = threshold } }

    // PIN Hash Storage (sensitive field)
    // TODO [WI-03 Constraint Conflict]: Criterion 6 requires EncryptedSharedPreferences
    // for PIN hash storage. However, androidx.security.crypto is NOT present in
    // app/build.gradle.kts and cannot be added without modifying that file (forbidden
    // by problem rules). Once the dependency is available, replace this block with:
    //
    //   import androidx.security.crypto.EncryptedSharedPreferences
    //   import androidx.security.crypto.MasterKey
    //
    //   private val masterKey = MasterKey.Builder(context)
    //       .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    //       .build()
    //
    //   private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
    //       context,
    //       "scrollshield_secure_prefs",
    //       masterKey,
    //       EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    //       EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    //   )
    //
    // Required build.gradle.kts addition:
    //   implementation("androidx.security:security-crypto:1.1.0-alpha06")

    private val sensitivePrefs: SharedPreferences =
        context.getSharedPreferences("scrollshield_sensitive_prefs", Context.MODE_PRIVATE)

    fun savePinHash(profileId: String, pinHash: String) {
        sensitivePrefs.edit().putString("pin_hash_$profileId", pinHash).apply()
    }

    fun getPinHash(profileId: String): String? =
        sensitivePrefs.getString("pin_hash_$profileId", null)

    fun clearPinHash(profileId: String) {
        sensitivePrefs.edit().remove("pin_hash_$profileId").apply()
    }
}
```

## File 7: `app/src/main/java/com/scrollshield/di/DatabaseModule.kt` (modify)

```kotlin
package com.scrollshield.di

import android.content.Context
import androidx.room.Room
import com.scrollshield.data.db.ScrollShieldDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScrollShieldDatabase(
        @ApplicationContext context: Context
    ): ScrollShieldDatabase {
        // Optional SQLCipher support — OFF by default.
        // To enable:
        //   1. Add to build.gradle.kts:
        //        implementation("net.zetetic:android-database-sqlcipher:4.5.4")
        //        implementation("androidx.sqlite:sqlite-ktx:2.4.0")
        //   2. Uncomment:
        // val passphrase: ByteArray = SQLiteDatabase.getBytes("your-passphrase".toCharArray())
        // val factory = SupportFactory(passphrase)
        // return Room.databaseBuilder(context, ScrollShieldDatabase::class.java, "scrollshield.db")
        //     .openHelperFactory(factory)
        //     .fallbackToDestructiveMigration()
        //     .build()

        return Room.databaseBuilder(
            context,
            ScrollShieldDatabase::class.java,
            "scrollshield.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
```
