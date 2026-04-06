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
