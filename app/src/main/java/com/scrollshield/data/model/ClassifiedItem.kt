package com.scrollshield.data.model

enum class Classification {
    ORGANIC, OFFICIAL_AD, INFLUENCER_PROMO,
    ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, EDUCATIONAL,
    UNKNOWN  // Unclassifiable — maps to SHOW_LOW_CONF
}

enum class SkipDecision {
    SHOW,           // Let the user see this item
    SKIP_AD,        // Auto-skip: official ad or influencer promo
    SKIP_BLOCKED,   // Auto-skip: blocked category
    SKIP_CHILD,     // Auto-skip: child-unsafe content
    SHOW_LOW_CONF   // Low confidence — fail open, let through
}

enum class TopicCategory(val index: Int, val label: String) {
    COMEDY(0, "Comedy/Humor"), MUSIC(1, "Music/Dance"), FOOD(2, "Food/Cooking"),
    SPORTS(3, "Sports/Fitness"), FASHION(4, "Fashion/Beauty"), TECH(5, "Tech/Science"),
    EDUCATION(6, "Education/Learning"), GAMING(7, "Gaming"), FINANCE(8, "Finance/Business"),
    POLITICS(9, "Politics/Activism"), ANIMALS(10, "Animals/Pets"), TRAVEL(11, "Travel/Adventure"),
    ART(12, "Art/Creativity"), NEWS(13, "News/Current Events"), RELATIONSHIPS(14, "Relationships/Social"),
    CARS(15, "Cars/Automotive"), HOME(16, "Home/DIY"), PARENTING(17, "Parenting/Family"),
    HEALTH(18, "Health/Wellness"), NATURE(19, "Nature/Environment");

    companion object {
        fun fromIndex(i: Int) = entries.first { it.index == i }
    }
}

data class ClassifiedItem(
    val feedItem: FeedItem,
    val classification: Classification,
    val confidence: Float,              // 0.0 to 1.0
    val topicVector: FloatArray,        // 20-dimensional, maps to TopicCategory entries
    val topicCategory: TopicCategory,   // Dominant topic from topicVector argmax
    /** Which tier classified it: 0 = text fast-path, 1 = visual, 2 = deep text */
    val tier: Int,
    val latencyMs: Long,
    val classifiedAt: Long,             // Unix epoch ms when classification was performed
    val skipDecision: SkipDecision      // Pre-computed skip/show decision
)
