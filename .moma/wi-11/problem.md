# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement WI-11: Onboarding & Settings UI for the ScrollShield Android app.

Build the complete onboarding flow (9 screens) and the settings screen with all user-configurable options using Jetpack Compose.

### Onboarding Flow (9 steps)

1. **Welcome**: Value prop + "Get Started" button
2. **App selection**: Checkboxes for installed target apps. Detection: `PackageManager.getInstalledApplications()` filtered against target package list (`com.zhiliaoapp.musically`, `com.instagram.android`, `com.google.android.youtube`)
3. **Interest selection**: 20-topic card grid (all TopicCategory values), select 3-8
4. **Blocked categories**: Toggles for gambling, diet, crypto, political outrage, clickbait, explicit ads. All off by default.
5. **Time budget**: Slider per app, 15-120 min, default 30. Includes "Unlimited" option.
6. **Feature toggles**: Ad Counter (on by default), Scroll Mask (off by default)
7. **Child profile prompt**: "Will children use this phone?" -> if yes, setup child profile (see ChildProfileSetup below)
8. **Permissions**:
   - Accessibility Service: navigate user to system settings, verify activation on return, re-prompt with explanation if not enabled
   - Overlay: `Settings.canDrawOverlays()` check, navigate to `ACTION_MANAGE_OVERLAY_PERMISSION` if needed
   - Screen Capture (MediaProjection): Launch `MediaProjectionManager.createScreenCaptureIntent()`. Explain: "ScrollShield uses screen capture to visually detect ads from the actual pixels on screen -- this is the primary protection method and cannot be defeated by apps hiding their labels." If denied: show warning that visual classification is unavailable and protection will rely on text-only detection.
   - Clear explanations for each permission
9. **Done**: Confirmation + first-session tip

### Child Profile Setup
- Pre-selected blocks: gambling, diet, crypto, explicit ads, alcohol
- Pre-selected classification blocks: ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, INFLUENCER_PROMO
- Mask always on, not dismissable
- Time budget default: 15 min/app
- 4-digit PIN for parent to switch profiles

### Settings Screen
- Profile switcher at top
- Per-profile: all onboarding choices editable
- Advanced: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size, status dot thresholds, visual classification toggle (default: on), MediaProjection re-grant button
- Data section: export CSV/JSON, delete all data
- Child profile management: edit, reset, change PIN
- Authentication: Access to child profile settings requires biometric or PIN authentication

### Settings Timing Rules
- Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session
- Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active

## Context Files

- app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- app/src/main/java/com/scrollshield/data/db/ProfileDao.kt
- app/src/main/java/com/scrollshield/data/preferences/UserPreferencesStore.kt
- app/src/main/java/com/scrollshield/profile/ProfileManager.kt
- app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt
- app/src/main/java/com/scrollshield/profile/ProfileSwitcher.kt
- app/src/main/java/com/scrollshield/feature/counter/AdCounterManager.kt
- app/build.gradle.kts
- app/src/main/java/com/scrollshield/di/ProfileModule.kt

## Target Files (to modify)

- app/src/main/java/com/scrollshield/ui/onboarding/OnboardingScreen.kt
- app/src/main/java/com/scrollshield/ui/onboarding/InterestSelectionScreen.kt
- app/src/main/java/com/scrollshield/ui/onboarding/BlockedCategoryScreen.kt
- app/src/main/java/com/scrollshield/ui/onboarding/TimeBudgetScreen.kt
- app/src/main/java/com/scrollshield/ui/onboarding/ChildProfileSetupScreen.kt
- app/src/main/java/com/scrollshield/ui/settings/SettingsScreen.kt

## Rules & Constraints

- All UI must use Jetpack Compose (no View-based screens)
- Use Hilt for dependency injection
- Use StateFlow/Flow for reactive state management
- Don't modify any existing files outside the target list without confirmation
- Keep function signatures of existing classes (ProfileManager, ProfileDao, etc.) stable
- Child profile mask must not be dismissable
- Profile changes take effect next session; feature toggles take effect immediately
- Onboarding must complete in < 2 minutes (keep screens focused)

## Review Criteria

1. All 9 onboarding steps are present and correctly ordered per the spec
2. All 20 TopicCategory values displayed in interest selection grid with 3-8 selection constraint
3. Child profile setup applies correct defaults from ChildProfileConfig (blocked categories, classifications, mask always on, 15 min budget, PIN)
4. Settings screen exposes all onboarding choices as editable, plus Advanced section (scoring weights, CPM overrides, visual classification toggle, MediaProjection re-grant)
5. Permission requests (Accessibility, Overlay, MediaProjection) include clear explanations and handle denial gracefully
6. Correct timing rules enforced -- profile changes at next session, feature toggles immediate
7. Biometric/PIN authentication required before accessing child profile settings
8. Delete all data wipes database + preferences completely
9. App selection screen only shows installed target apps via PackageManager detection
10. Proper use of Hilt DI, StateFlow, and Compose patterns consistent with existing codebase

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew assembleDebug
```
