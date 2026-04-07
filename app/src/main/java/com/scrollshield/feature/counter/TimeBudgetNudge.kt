package com.scrollshield.feature.counter

/**
 * Budget bracket — pure function of elapsed/budget ratio and child flag.
 *
 * Contract for WI-09/10: when state == CHILD_HARD_STOP, the mask should
 * skip all remaining content. AdCounterManager broadcasts ACTION_CHILD_HARD_STOP
 * on transition into this state.
 */
enum class BudgetState { UNDER, AT_80, AT_100, AT_120, CHILD_HARD_STOP }

object TimeBudgetNudge {

    /**
     * Pure evaluator. Returns UNDER when budgetMinutes <= 0 (no budget set).
     */
    fun evaluate(elapsedMinutes: Float, budgetMinutes: Float, isChild: Boolean): BudgetState {
        if (budgetMinutes <= 0f) return BudgetState.UNDER
        val ratio = elapsedMinutes / budgetMinutes
        return when {
            ratio >= 1.2f -> BudgetState.AT_120
            ratio >= 1.0f -> if (isChild) BudgetState.CHILD_HARD_STOP else BudgetState.AT_100
            ratio >= 0.8f -> BudgetState.AT_80
            else -> BudgetState.UNDER
        }
    }

    /** Non-blocking visual delegation. */
    fun applyToOverlay(overlay: AdCounterOverlay, state: BudgetState) {
        overlay.applyBudgetState(state)
    }

    /** Contract for WI-09/10 mask integration. */
    fun shouldHardStopMask(state: BudgetState): Boolean = state == BudgetState.CHILD_HARD_STOP
}
