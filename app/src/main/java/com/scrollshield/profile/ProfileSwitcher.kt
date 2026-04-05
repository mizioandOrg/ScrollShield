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
        if (match) {
            failedAttempts = 0
            lockoutEndTimeMs = 0L
        } else {
            failedAttempts++
            lockoutEndTimeMs = System.currentTimeMillis() + getLockoutDurationMs()
        }
        return match
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutEndTimeMs

    fun getRemainingLockoutMs(): Long =
        (lockoutEndTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)

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

        val currentId = preferencesStore.activeProfileId.first()
            ?: return SwitchResult.PROFILE_NOT_FOUND
        val currentProfile = profileDao.getById(currentId)
            ?: return SwitchResult.PROFILE_NOT_FOUND
        profileDao.getById(targetProfileId)
            ?: return SwitchResult.PROFILE_NOT_FOUND

        if (currentProfile.isChildProfile && currentProfile.pinProtected
            && !pinVerified && !biometricVerified) {
            return SwitchResult.PIN_REQUIRED
        }

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

    fun clearPendingSwitch() {
        pendingProfileId = null
    }

    suspend fun updateFeatureToggles(
        profileId: String,
        maskEnabled: Boolean,
        counterEnabled: Boolean,
        maskDismissable: Boolean
    ): Boolean {
        val profile = profileDao.getById(profileId) ?: return false
        profileDao.upsert(
            profile.copy(
                maskEnabled = maskEnabled,
                counterEnabled = counterEnabled,
                maskDismissable = maskDismissable,
                updatedAt = System.currentTimeMillis()
            )
        )
        val activeId = preferencesStore.activeProfileId.first()
        return profileId == activeId && !isSessionActive
    }

    fun isWithinSchedule(schedule: Pair<LocalTime, LocalTime>?): Boolean {
        if (schedule == null) return false
        val now = LocalTime.now()
        val (start, end) = schedule
        return if (start.isBefore(end)) {
            !now.isBefore(start) && now.isBefore(end)
        } else {
            !now.isBefore(start) || now.isBefore(end)
        }
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
