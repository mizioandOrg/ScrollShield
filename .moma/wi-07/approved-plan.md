# Approved Plan — Iteration 3 (Score: 10/10)

## Files to Create

### 1. ChildProfileConfig.kt
`app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt`

```kotlin
package com.scrollshield.profile

import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.TopicCategory

/**
 * Pre-selected defaults for child profiles.
 *
 * CATEGORY MAPPING NOTE: The WI-07 spec defines five blocked content categories
 * by name: "gambling", "diet", "crypto", "explicit ads", "alcohol". The TopicCategory
 * enum (used by UserProfile.blockedCategories) has no direct equivalents for any of
 * these. BLOCKED_TOPIC_CATEGORIES contains the closest available TopicCategory values
 * as best-effort approximations. The canonical spec names are preserved in
 * BLOCKED_CATEGORY_LABELS for display, logging, and audit purposes.
 */
object ChildProfileConfig {

    val BLOCKED_CATEGORY_LABELS: List<String> = listOf(
        "gambling", "diet", "crypto", "explicit ads", "alcohol"
    )

    val BLOCKED_TOPIC_CATEGORIES: Set<TopicCategory> = setOf(
        TopicCategory.FINANCE,  // closest match for "gambling" and "crypto"
        TopicCategory.HEALTH,   // closest match for "diet" and "alcohol"
        TopicCategory.FASHION   // closest match for "explicit ads"
    )

    val BLOCKED_CLASSIFICATIONS: Set<Classification> = setOf(
        Classification.ENGAGEMENT_BAIT,
        Classification.OUTRAGE_TRIGGER,
        Classification.INFLUENCER_PROMO
    )

    const val MASK_ENABLED: Boolean = true
    const val MASK_DISMISSABLE: Boolean = false
    const val COUNTER_ENABLED: Boolean = true
    const val DEFAULT_TIME_BUDGET_MINUTES: Int = 15
    const val PIN_PROTECTED: Boolean = true
}
```

### 2. ProfileManager.kt
`app/src/main/java/com/scrollshield/profile/ProfileManager.kt`

```kotlin
package com.scrollshield.profile

import com.scrollshield.data.db.ProfileDao
import com.scrollshield.data.model.ScoringWeights
import com.scrollshield.data.model.UserProfile
import com.scrollshield.data.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    private val profileDao: ProfileDao,
    private val preferencesStore: UserPreferencesStore
) {
    fun getAllProfiles(): Flow<List<UserProfile>> = profileDao.getAllProfiles()
    suspend fun getProfileById(id: String): UserProfile? = profileDao.getById(id)
    fun getActiveProfileId(): Flow<String?> = preferencesStore.activeProfileId

    suspend fun createDefaultProfile(name: String, interestVector: FloatArray): UserProfile {
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            id = UUID.randomUUID().toString(), name = name, isChildProfile = false,
            interestVector = interestVector, blockedCategories = emptySet(),
            blockedClassifications = emptySet(), timeBudgets = emptyMap(),
            maskEnabled = false, counterEnabled = false, maskDismissable = true,
            pinProtected = false, parentPinHash = null, satisfactionHistory = emptyList(),
            scoringWeights = ScoringWeights(), createdAt = now, updatedAt = now,
            autoActivateSchedule = null
        )
        profileDao.upsert(profile)
        preferencesStore.setActiveProfileId(profile.id)
        return profile
    }

    suspend fun createChildProfile(
        name: String, parentPinHash: String,
        timeBudgets: Map<String, Int> = mapOf("default" to ChildProfileConfig.DEFAULT_TIME_BUDGET_MINUTES),
        autoActivateSchedule: Pair<LocalTime, LocalTime>? = null
    ): UserProfile {
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            id = UUID.randomUUID().toString(), name = name, isChildProfile = true,
            interestVector = FloatArray(20) { 0f },
            blockedCategories = ChildProfileConfig.BLOCKED_TOPIC_CATEGORIES,
            blockedClassifications = ChildProfileConfig.BLOCKED_CLASSIFICATIONS,
            timeBudgets = timeBudgets,
            maskEnabled = ChildProfileConfig.MASK_ENABLED,
            counterEnabled = ChildProfileConfig.COUNTER_ENABLED,
            maskDismissable = ChildProfileConfig.MASK_DISMISSABLE,
            pinProtected = ChildProfileConfig.PIN_PROTECTED,
            parentPinHash = parentPinHash, satisfactionHistory = emptyList(),
            scoringWeights = ScoringWeights(), createdAt = now, updatedAt = now,
            autoActivateSchedule = autoActivateSchedule
        )
        profileDao.upsert(profile)
        return profile
    }

    suspend fun updateProfile(profile: UserProfile): UserProfile {
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        profileDao.upsert(updated)
        return updated
    }

    suspend fun deleteProfile(profile: UserProfile) { profileDao.delete(profile) }
    suspend fun setActiveProfile(profileId: String) { preferencesStore.setActiveProfileId(profileId) }
}
```

### 3. ProfileSwitcher.kt
`app/src/main/java/com/scrollshield/profile/ProfileSwitcher.kt`

```kotlin
package com.scrollshield.profile

import android.content.ContentResolver
import android.provider.Settings
import com.scrollshield.data.db.ProfileDao
import com.scrollshield.data.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileSwitcher @Inject constructor(
    private val profileDao: ProfileDao,
    private val preferencesStore: UserPreferencesStore,
    private val contentResolver: ContentResolver
) {
    enum class SwitchResult {
        APPLIED, DEFERRED, PIN_REQUIRED, LOCKED_OUT, PROFILE_NOT_FOUND
    }

    interface BiometricAuthenticator {
        suspend fun authenticate(): Boolean
    }

    companion object {
        private const val LOCKOUT_THRESHOLD_1 = 3
        private const val LOCKOUT_THRESHOLD_2 = 5
        private const val LOCKOUT_THRESHOLD_3 = 10
        private const val LOCKOUT_DURATION_1_MS = 30_000L
        private const val LOCKOUT_DURATION_2_MS = 300_000L
        private const val LOCKOUT_DURATION_3_MS = 1_800_000L
    }

    private var failedAttempts: Int = 0
    private var lockoutEndTimeMs: Long = 0L
    private var pendingProfileId: String? = null
    var isSessionActive: Boolean = false

    private fun hashPin(pin: String): String {
        val salt = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val salted = "$salt:$pin"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(salted.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun setPin(profileId: String, pin: String) {
        val hash = hashPin(pin)
        preferencesStore.savePinHash(profileId, hash)
        val profile = profileDao.getById(profileId) ?: return
        profileDao.upsert(profile.copy(parentPinHash = hash, updatedAt = System.currentTimeMillis()))
    }

    suspend fun verifyPin(profileId: String, pin: String): Boolean {
        if (isLockedOut()) return false
        val storedHash = preferencesStore.getPinHash(profileId)
        val match = storedHash != null && storedHash == hashPin(pin)
        if (match) { failedAttempts = 0; lockoutEndTimeMs = 0L }
        else { failedAttempts++; lockoutEndTimeMs = System.currentTimeMillis() + getLockoutDurationMs() }
        return match
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutEndTimeMs
    fun getRemainingLockoutMs(): Long = (lockoutEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun getLockoutDurationMs(): Long = when {
        failedAttempts >= LOCKOUT_THRESHOLD_3 -> LOCKOUT_DURATION_3_MS
        failedAttempts >= LOCKOUT_THRESHOLD_2 -> LOCKOUT_DURATION_2_MS
        failedAttempts >= LOCKOUT_THRESHOLD_1 -> LOCKOUT_DURATION_1_MS
        else -> 0L
    }

    suspend fun switchProfile(
        targetProfileId: String,
        pinVerified: Boolean = false,
        biometricVerified: Boolean = false
    ): SwitchResult {
        if (isLockedOut()) return SwitchResult.LOCKED_OUT
        val currentId = preferencesStore.activeProfileId.first() ?: return SwitchResult.PROFILE_NOT_FOUND
        val currentProfile = profileDao.getById(currentId) ?: return SwitchResult.PROFILE_NOT_FOUND
        profileDao.getById(targetProfileId) ?: return SwitchResult.PROFILE_NOT_FOUND

        if (currentProfile.isChildProfile && currentProfile.pinProtected && !pinVerified && !biometricVerified)
            return SwitchResult.PIN_REQUIRED

        if (isSessionActive) {
            pendingProfileId = targetProfileId
            return SwitchResult.DEFERRED
        }

        preferencesStore.setActiveProfileId(targetProfileId)
        pendingProfileId = null
        return SwitchResult.APPLIED
    }

    suspend fun applyPendingSwitch(): Boolean {
        val targetId = pendingProfileId ?: return false
        preferencesStore.setActiveProfileId(targetId)
        pendingProfileId = null
        return true
    }

    fun hasPendingSwitch(): Boolean = pendingProfileId != null
    fun clearPendingSwitch() { pendingProfileId = null }

    suspend fun updateFeatureToggles(
        profileId: String, maskEnabled: Boolean, counterEnabled: Boolean, maskDismissable: Boolean
    ): Boolean {
        val profile = profileDao.getById(profileId) ?: return false
        profileDao.upsert(profile.copy(
            maskEnabled = maskEnabled, counterEnabled = counterEnabled,
            maskDismissable = maskDismissable, updatedAt = System.currentTimeMillis()
        ))
        val activeId = preferencesStore.activeProfileId.first()
        return profileId == activeId && !isSessionActive
    }

    fun isWithinSchedule(schedule: Pair<LocalTime, LocalTime>?): Boolean {
        if (schedule == null) return false
        val now = LocalTime.now()
        val (start, end) = schedule
        return if (start.isBefore(end)) !now.isBefore(start) && now.isBefore(end)
        else !now.isBefore(start) || now.isBefore(end)
    }

    suspend fun checkAutoActivation() {
        val profiles = profileDao.getAllProfiles().first()
        val activeId = preferencesStore.activeProfileId.first()

        for (profile in profiles) {
            if (profile.isChildProfile && isWithinSchedule(profile.autoActivateSchedule)) {
                if (profile.id != activeId) {
                    if (!isSessionActive) preferencesStore.setActiveProfileId(profile.id)
                    else pendingProfileId = profile.id
                }
                return
            }
        }

        val currentProfile = activeId?.let { profileDao.getById(it) }
        if (currentProfile?.isChildProfile == true) {
            val defaultProfile = profiles.firstOrNull { !it.isChildProfile }
            if (defaultProfile != null && defaultProfile.id != activeId) {
                if (!isSessionActive) preferencesStore.setActiveProfileId(defaultProfile.id)
                else pendingProfileId = defaultProfile.id
            }
        }
    }
}
```

### 4. ProfileModule.kt (DI)
`app/src/main/java/com/scrollshield/di/ProfileModule.kt`

```kotlin
package com.scrollshield.di

import android.content.ContentResolver
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
```
