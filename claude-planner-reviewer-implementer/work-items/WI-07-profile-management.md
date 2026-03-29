# WI-07: Profile Management

## Source
- Architecture: Profile Switching (Parent -> Child)
- Data Models: UserProfile, ScoringWeights
- Module 5: Child Profile Setup
- Security & Privacy: PIN Security

## Goal
Implement profile management including default profile, child profile, profile switching, PIN protection, and auto-activation scheduling.

## Context
ScrollShield supports two profile types: a default (parent) profile and a child profile. The child profile has restrictive settings. Profile switching requires PIN authentication. Child profile auto-activation can be scheduled.

## Dependencies
- **Hard**: WI-02 (UserProfile model), WI-03 (ProfileDao, UserPreferencesStore)
- **Integration**: WI-01 (DI)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/profile/ProfileManager.kt`
- `app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt`
- `app/src/main/java/com/scrollshield/profile/ProfileSwitcher.kt`

## Detailed Specification

### ProfileManager
- Create/update/delete profiles via ProfileDao
- Manage active profile (stored in UserPreferencesStore)
- Default profile created during onboarding with user's interest selections

### ChildProfileConfig
Pre-selected defaults for child profiles:
- Blocked categories: gambling, diet, crypto, explicit ads, alcohol
- Blocked classifications: `ENGAGEMENT_BAIT`, `OUTRAGE_TRIGGER`, `INFLUENCER_PROMO`
- `maskEnabled = true`, `maskDismissable = false`
- `counterEnabled = true`
- Time budget default: 15 min/app
- `pinProtected = true`

### ProfileSwitcher
- Switch between default and child profiles
- PIN authentication required to switch FROM child profile
- PIN stored as SHA-256 hash with device-specific salt (from `Settings.Secure.ANDROID_ID`)
- Lockout escalation: 3 failed attempts -> 30s lockout, 5 -> 5min, 10 -> 30min

### Auto-Activation Schedule
- `autoActivateSchedule: Pair<LocalTime, LocalTime>?` (start, end)
- When set, automatically switch to child profile during the scheduled window
- Parent can configure via schedule picker

### Settings Timing
- Profile changes (blocked categories, interest vector, scoring weights) take effect at the next session
- Feature toggles (counter on/off, mask on/off) take effect immediately if no session is active

## Acceptance Criteria
- Default profile can be created with all fields
- Child profile created with correct pre-selected defaults
- PIN authentication works with SHA-256 + device salt
- Lockout escalation enforced (3 -> 30s, 5 -> 5min, 10 -> 30min)
- Profile switch updates active profile in preferences
- Auto-activation schedule triggers profile switch at correct times
- Settings timing rules correctly enforced

## Notes
- Biometric authentication for child profile settings access is specified in the original spec. Implement using `BiometricPrompt` API as an alternative to PIN.
