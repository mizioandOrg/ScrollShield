package com.scrollshield.classification

import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkipDecisionEngine @Inject constructor() {

    fun decide(
        classification: Classification,
        confidence: Float,
        topicCategory: TopicCategory,
        profile: UserProfile
    ): SkipDecision {
        // EDUCATIONAL is never auto-skipped
        if (classification == Classification.EDUCATIONAL) return SkipDecision.SHOW

        // Low confidence fails open
        if (confidence < 0.5f) return SkipDecision.SHOW_LOW_CONF

        // Child profile: stricter rules
        if (profile.isChildProfile) {
            if (classification == Classification.OFFICIAL_AD ||
                classification == Classification.INFLUENCER_PROMO) {
                return SkipDecision.SKIP_CHILD
            }
            if (classification == Classification.ENGAGEMENT_BAIT ||
                classification == Classification.OUTRAGE_TRIGGER) {
                return SkipDecision.SKIP_CHILD
            }
        }

        // Check if classification is in user's blocked classifications
        if (classification in profile.blockedClassifications) {
            return when (classification) {
                Classification.OFFICIAL_AD, Classification.INFLUENCER_PROMO -> SkipDecision.SKIP_AD
                else -> SkipDecision.SKIP_BLOCKED
            }
        }

        // Check if topic is in user's blocked categories
        if (topicCategory in profile.blockedCategories) {
            return SkipDecision.SKIP_BLOCKED
        }

        // Mask must be enabled for auto-skipping
        if (!profile.maskEnabled) return SkipDecision.SHOW

        // Default: show
        return SkipDecision.SHOW
    }
}
