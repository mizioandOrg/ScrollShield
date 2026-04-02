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
