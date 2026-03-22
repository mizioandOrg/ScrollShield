# WI-11: Onboarding & Settings UI

## Source
- Module 5: Onboarding & Settings (entire section)
- File Structure: `ui/onboarding/`, `ui/settings/`

## Goal
Implement the onboarding flow (9 screens) and the settings screen with all user-configurable options.

## Context
Onboarding guides first-time users through app selection, interest selection, blocked categories, time budgets, feature toggles, child profile setup, and permissions. Settings allows editing all choices post-onboarding. Target: complete onboarding in < 2 minutes.

## Dependencies
- **Hard**: WI-02 (UserProfile, TopicCategory), WI-03 (ProfileDao, UserPreferencesStore), WI-07 (ProfileManager, ChildProfileConfig)
- **Integration**: WI-08 (feature toggle for counter), WI-09 (feature toggle for mask)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/ui/onboarding/OnboardingScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/InterestSelectionScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/BlockedCategoryScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/TimeBudgetScreen.kt`
- `app/src/main/java/com/scrollshield/ui/onboarding/ChildProfileSetupScreen.kt`
- `app/src/main/java/com/scrollshield/ui/settings/SettingsScreen.kt`

## Detailed Specification

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
   - **Screen Capture (MediaProjection)**: Launch `MediaProjectionManager.createScreenCaptureIntent()`. Explain: "ScrollShield uses screen capture to visually detect ads from the actual pixels on screen — this is the primary protection method and cannot be defeated by apps hiding their labels." If denied: show warning that visual classification is unavailable and protection will rely on text-only detection.
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
- Advanced: scoring weight sliders, CPM overrides, pre-scan buffer size, extension size, status dot thresholds, **visual classification toggle** (default: on), **MediaProjection re-grant button**
- Data section: export CSV/JSON, delete all data
- Child profile management: edit, reset, change PIN
- **Authentication**: Access to child profile settings requires biometric or PIN authentication

### Settings Timing Rules
- Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session
- Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active

## Acceptance Criteria
- Onboarding completes in < 2 min
- All 20 topic categories displayed in grid
- Preferences persist across app restarts
- Child profile PIN works
- Settings changes apply at correct timing (next session vs. immediate)
- Biometric/PIN required for child profile settings
- Delete all data wipes everything (database + preferences)
- App selection only shows installed target apps
- MediaProjection permission requested during onboarding with clear explanation
- Visual classification toggle in Advanced settings
- MediaProjection denial shows warning about reduced protection

## Notes
- All UI uses Jetpack Compose per the tech stack.
- The SQLCipher toggle (optional database encryption) belongs in Settings > Advanced.
