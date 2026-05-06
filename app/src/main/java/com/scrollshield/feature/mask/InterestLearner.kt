package com.scrollshield.feature.mask

import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.UserProfile
import com.scrollshield.profile.ProfileManager

/**
 * Learns user interest preferences over time using Exponential Moving Average (EMA)
 * on the topic category vectors of viewed (non-skipped) items.
 *
 * Not Hilt-injected — instantiated manually by ScrollMaskManager.
 * Receives SessionDao via ScrollMaskManager.initialize(sessionDao).
 */
class InterestLearner(
    private val profileManager: ProfileManager,
    private val sessionDao: SessionDao
) {

    companion object {
        const val ALPHA_EARLY = 0.2f
        const val ALPHA_MATURE = 0.05f
        const val EARLY_SESSION_THRESHOLD = 5
        const val VECTOR_SIZE = 20
    }

    /**
     * Update the user's interest vector when a non-skipped item is viewed.
     * Child profiles are excluded from interest learning.
     *
     * @param profile the active user profile
     * @param item the classified item that was viewed (not skipped)
     */
    suspend fun onItemViewed(profile: UserProfile, item: ClassifiedItem) {
        if (profile.isChildProfile) return

        val alpha = computeAlpha(profile.id)
        val oldVector = profile.interestVector
        val newVector = FloatArray(VECTOR_SIZE)

        // One-hot observed vector at the item's topic category index
        val observedIndex = item.topicCategory.index

        for (i in 0 until VECTOR_SIZE) {
            val observed = if (i == observedIndex) 1f else 0f
            newVector[i] = (1f - alpha) * oldVector[i] + alpha * observed
        }

        profileManager.updateProfile(profile.copy(interestVector = newVector))
    }

    /**
     * Compute the EMA alpha based on the number of sessions the user has had.
     * Early sessions (< 5) use a higher alpha for faster adaptation,
     * mature profiles use a lower alpha for stability.
     */
    private suspend fun computeAlpha(profileId: String): Float {
        val sessionCount = try {
            sessionDao.getSessionsByProfileSince(profileId, 0L).size
        } catch (_: Exception) {
            0
        }
        return if (sessionCount < EARLY_SESSION_THRESHOLD) ALPHA_EARLY else ALPHA_MATURE
    }
}
