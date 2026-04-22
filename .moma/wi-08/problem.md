# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-08 (Ad Counter Feature) for the ScrollShield Android app located at `/home/devuser/dev-worktree-1`. Build the ad counter overlay feature: session management, counting logic, overlay UI (pill + summary card), time budget nudges, satisfaction survey, and the shared `OverlayService` lifecycle manager. The Ad Counter is purely observational — it counts every ad served during a session and displays the count in a floating overlay pill. It must never modify the feed. See `/home/devuser/dev-worktree-1/work-items/WI-08-ad-counter.md` for the full specification.

## Context Files

- /home/devuser/dev-worktree-1/work-items/WI-08-ad-counter.md
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/SessionRecord.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/service/FeedInterceptionService.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/classification/ClassificationPipeline.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/data/db/SessionDao.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/profile/ProfileManager.kt

## Target Files (to modify)

- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/feature/counter/AdCounterOverlay.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/feature/counter/SessionSummaryCard.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/feature/counter/TimeBudgetNudge.kt
- /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/service/OverlayService.kt

## Rules & Constraints

- Do not modify any of the context (read-only) files listed above.
- Do not modify the feed in any way. The Ad Counter is purely observational.
- All overlays must use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` so there is no touch interception on the underlying app.
- The `OverlayService` interface must be reusable by WI-09 / WI-10 (Scroll Mask) — define a shared lifecycle manager interface, not a counter-only service.
- Check `Settings.canDrawOverlays()` before rendering any overlay; if not granted, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` with the package URI.
- Stay within the packages `com.scrollshield.feature.counter` and `com.scrollshield.service`. Do not create files outside the listed target paths.

## Review Criteria

1. Session lifecycle correct: starts on `APP_FOREGROUND` (loads active profile, resets counter), ends on `APP_BACKGROUND` (writes `SessionRecord`).
2. Checkpoint every 60s writes a partial `SessionRecord` with `endedNormally = false`, overwritten on normal session end.
3. Counter subscribes to `ClassifiedItem` events from both pre-scan and live phases, increments on `OFFICIAL_AD` or `INFLUENCER_PROMO`, and updates within 200ms of classification.
4. Tracks brands, categories, and the classification tier used (Tier 0 text fast-path / Tier 1 visual / Tier 2 deep text) for the detection method breakdown.
5. Revenue estimate computed as `adCount * platformCPM / 1000` using configurable per-platform CPM defaults (TikTok $10, Instagram $12, YouTube $15) read from Settings > Advanced.
6. Pill UI matches spec: top-center anchored `TYPE_APPLICATION_OVERLAY`, semi-transparent dark blur, monospace 11sp, height 32dp, radius 16dp, L->R layout (status dot | ad count | revenue | session time), draggable with position persisted across sessions, status dot thresholds (green 0-2, amber 3-10, red >10) configurable in Settings > Advanced, and "Ads detected: X / Ads skipped: Y" when mask is active.
7. Summary card (bottom-anchored, 40% height) shows ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration, and detection method breakdown; export writes valid JSON to Downloads with a top-level `version` key.
8. Time budget nudges fire at 80% ("5 min left"), 100% (flash + "Budget reached"), and 120% (red pill background); child profile hits a hard stop at 100% with "Time's up" and the mask skips remaining content.
9. Overlay permission is checked before rendering and all overlay views use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` so the underlying app receives all touches.
10. `OverlayService` is a single shared lifecycle manager (manages WindowManager add/remove for pill, loading, skip flash, summary) with an interface that WI-09/WI-10 mask overlays can plug into; post-session satisfaction survey (1-5 stars) displays on `APP_BACKGROUND`, auto-dismisses after 10s, and stores the result in `SessionRecord.satisfactionRating`.

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew :app:assembleDebug
```
