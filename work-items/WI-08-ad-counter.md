# WI-08: Ad Counter Feature

## Source
- Module 3: Ad Counter (entire section)
- File Structure: `feature/counter/`

## Goal
Implement the ad counter overlay feature: session management, counting logic, overlay UI (pill + summary card), time budget nudges, and satisfaction survey.

## Context
The Ad Counter is Feature 1 — purely observational. It counts every ad served during a session and displays the count in a floating overlay pill. It never modifies the feed.

## Dependencies
- **Hard**: WI-02 (ClassifiedItem, SessionRecord), WI-05 (lifecycle events, APP_FOREGROUND/BACKGROUND), WI-06 (ClassifiedItem events)
- **Integration**: WI-03 (SessionDao for persistence), WI-07 (active profile for budget thresholds)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt`
- `app/src/main/java/com/scrollshield/feature/counter/AdCounterOverlay.kt`
- `app/src/main/java/com/scrollshield/feature/counter/SessionSummaryCard.kt`
- `app/src/main/java/com/scrollshield/feature/counter/TimeBudgetNudge.kt`
- `app/src/main/java/com/scrollshield/service/OverlayService.kt`

## Detailed Specification

### Session Management
- Starts on `APP_FOREGROUND`: load active profile, reset counter
- Ends on `APP_BACKGROUND`: write SessionRecord, show post-session satisfaction survey (1-5 stars, auto-dismiss after 10s, stored in `SessionRecord.satisfactionRating`)
- Checkpoint every 60s: write partial SessionRecord to Room with `endedNormally = false`. Overwritten on normal session end.

### Counting Logic
- Subscribes to ClassifiedItem events from both pre-scan phase and live scrolling
- Increments on `OFFICIAL_AD` or `INFLUENCER_PROMO`
- Tracks brands, categories
- Revenue estimate: `adCount * platformCPM / 1000`
  - TikTok: $10 CPM
  - Instagram: $12 CPM
  - YouTube: $15 CPM
  - CPM values configurable in Settings > Advanced
  - Note: rough estimates based on public CPM averages, not actual platform revenue
- When mask is active: displays "Ads detected: X" and "Ads skipped: Y"

### Overlay Permission
- Check `Settings.canDrawOverlays()` before rendering
- If not granted, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` with package URI
- Touch passthrough: all overlays use `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`

### UI — Pill (collapsed)
- Floating `TYPE_APPLICATION_OVERLAY`, anchored top-center
- Semi-transparent dark blur
- L->R: status dot | ad count (bold white) | separator | revenue (muted) | separator | session time (muted)
- Status dot: green (0-2), amber (3-10), red (>10) with pulse animation. Thresholds configurable in Settings > Advanced.
- Monospace 11sp, height 32dp, radius 16dp
- Draggable, remembers position
- Tap to expand

### UI — Summary Card (expanded)
- Bottom-anchored, 40% height
- Shows: ads detected, ads skipped, ad-to-content ratio, brand pills, category pills, revenue, duration
- Close -> collapse
- Export -> JSON to Downloads. JSON schema includes a `version` key for forward compatibility.

### Time Budget Nudges
Passive visual changes on the pill — no blocking:
- 80%: "5 min left" text
- 100%: flash + "Budget reached"
- 120%: pill background turns red
- Child profile at 100%: "Time's up" — mask skips all remaining content

### OverlayService
- `OverlayService.kt`: Single overlay lifecycle manager for pill, loading, skip flash, summary
- Manages WindowManager add/remove for all overlay views
- Handles overlay permission checks

## Acceptance Criteria
- Renders correctly above TikTok, Instagram, YouTube
- **Increments within 200ms of classification**
- Shows detected and skipped counts when mask active
- Correct status dot thresholds (green 0-2, amber 3-10, red >10)
- Draggable, position persists across sessions
- Valid JSON export with version key
- No touch interception on underlying app
- Budget nudges at correct thresholds (80%, 100%, 120%)
- Child hard stop at 100%
- Satisfaction survey displays on session end, auto-dismisses after 10s
- Checkpoint every 60s with `endedNormally = false`

## Notes
- The OverlayService is shared with the Scroll Mask feature (WI-09, WI-10). Define the service interface here; mask overlays plug into the same service.
