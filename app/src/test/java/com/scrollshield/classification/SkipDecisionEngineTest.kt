package com.scrollshield.classification

import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.ScoringWeights
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import org.junit.Test

class SkipDecisionEngineTest {

    private val engine = SkipDecisionEngine()

    @Test
    fun educationalAlwaysShows() {
        val p = profile()
        val d = engine.decide(Classification.EDUCATIONAL, 0.99f, TopicCategory.EDUCATION, p)
        check(d == SkipDecision.SHOW) { "Got $d" }
    }

    @Test
    fun lowConfidenceFailsOpen() {
        val p = profile()
        val d = engine.decide(Classification.OFFICIAL_AD, 0.49f, TopicCategory.TECH, p)
        check(d == SkipDecision.SHOW_LOW_CONF) { "Got $d" }
    }

    @Test
    fun childProfileSkipsOfficialAd() {
        val p = profile(isChild = true)
        val d = engine.decide(Classification.OFFICIAL_AD, 0.9f, TopicCategory.TECH, p)
        check(d == SkipDecision.SKIP_CHILD)
    }

    @Test
    fun childProfileSkipsOutrageTrigger() {
        val p = profile(isChild = true)
        val d = engine.decide(Classification.OUTRAGE_TRIGGER, 0.9f, TopicCategory.POLITICS, p)
        check(d == SkipDecision.SKIP_CHILD)
    }

    @Test
    fun blockedClassificationSkipsAd() {
        val p = profile(blocked = setOf(Classification.OFFICIAL_AD))
        val d = engine.decide(Classification.OFFICIAL_AD, 0.9f, TopicCategory.TECH, p)
        check(d == SkipDecision.SKIP_AD)
    }

    @Test
    fun blockedCategoryProducesSkipBlocked() {
        val p = profile(blockedCats = setOf(TopicCategory.GAMING))
        val d = engine.decide(Classification.ORGANIC, 0.9f, TopicCategory.GAMING, p)
        check(d == SkipDecision.SKIP_BLOCKED)
    }

    @Test
    fun unblockedShowsWhenMaskEnabled() {
        val p = profile()
        val d = engine.decide(Classification.ORGANIC, 0.9f, TopicCategory.MUSIC, p)
        check(d == SkipDecision.SHOW)
    }

    @Test
    fun unblockedShowsWhenMaskDisabled() {
        val p = profile(maskEnabled = false)
        val d = engine.decide(Classification.ORGANIC, 0.9f, TopicCategory.MUSIC, p)
        check(d == SkipDecision.SHOW)
    }

    private fun profile(
        isChild: Boolean = false,
        blocked: Set<Classification> = emptySet(),
        blockedCats: Set<TopicCategory> = emptySet(),
        maskEnabled: Boolean = true,
    ): UserProfile = UserProfile(
        id = "p1", name = "Test", isChildProfile = isChild,
        interestVector = FloatArray(20), blockedCategories = blockedCats,
        blockedClassifications = blocked, timeBudgets = emptyMap(),
        maskEnabled = maskEnabled, counterEnabled = true,
        maskDismissable = false, pinProtected = false,
        parentPinHash = null, satisfactionHistory = emptyList(),
        scoringWeights = ScoringWeights(), createdAt = 0L, updatedAt = 0L,
        autoActivateSchedule = null,
    )
}
