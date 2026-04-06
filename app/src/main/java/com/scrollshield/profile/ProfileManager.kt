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
            id = UUID.randomUUID().toString(),
            name = name,
            isChildProfile = false,
            interestVector = interestVector,
            blockedCategories = emptySet(),
            blockedClassifications = emptySet(),
            timeBudgets = emptyMap(),
            maskEnabled = false,
            counterEnabled = false,
            maskDismissable = true,
            pinProtected = false,
            parentPinHash = null,
            satisfactionHistory = emptyList(),
            scoringWeights = ScoringWeights(),
            createdAt = now,
            updatedAt = now,
            autoActivateSchedule = null
        )
        profileDao.upsert(profile)
        preferencesStore.setActiveProfileId(profile.id)
        return profile
    }

    suspend fun createChildProfile(
        name: String,
        parentPinHash: String,
        timeBudgets: Map<String, Int> = mapOf("default" to ChildProfileConfig.DEFAULT_TIME_BUDGET_MINUTES),
        autoActivateSchedule: Pair<LocalTime, LocalTime>? = null
    ): UserProfile {
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            isChildProfile = true,
            interestVector = FloatArray(20) { 0f },
            blockedCategories = ChildProfileConfig.BLOCKED_TOPIC_CATEGORIES,
            blockedClassifications = ChildProfileConfig.BLOCKED_CLASSIFICATIONS,
            timeBudgets = timeBudgets,
            maskEnabled = ChildProfileConfig.MASK_ENABLED,
            counterEnabled = ChildProfileConfig.COUNTER_ENABLED,
            maskDismissable = ChildProfileConfig.MASK_DISMISSABLE,
            pinProtected = ChildProfileConfig.PIN_PROTECTED,
            parentPinHash = parentPinHash,
            satisfactionHistory = emptyList(),
            scoringWeights = ScoringWeights(),
            createdAt = now,
            updatedAt = now,
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

    suspend fun deleteProfile(profile: UserProfile) {
        profileDao.delete(profile)
    }

    suspend fun setActiveProfile(profileId: String) {
        preferencesStore.setActiveProfileId(profileId)
    }
}
