# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-07 (Profile Management) for the ScrollShield Android app. Create three new classes in `app/src/main/java/com/scrollshield/profile/`:

1. **ProfileManager** — Create/update/delete profiles via ProfileDao. Manage active profile (stored in UserPreferencesStore). Default profile created during onboarding with user's interest selections.

2. **ChildProfileConfig** — Pre-selected defaults for child profiles: blocked categories (gambling, diet, crypto, explicit ads, alcohol), blocked classifications (ENGAGEMENT_BAIT, OUTRAGE_TRIGGER, INFLUENCER_PROMO), maskEnabled=true, maskDismissable=false, counterEnabled=true, time budget default 15 min/app, pinProtected=true.

3. **ProfileSwitcher** — Switch between default and child profiles. PIN authentication required to switch FROM child profile. PIN stored as SHA-256 hash with device-specific salt (Settings.Secure.ANDROID_ID). Lockout escalation: 3 failed attempts -> 30s, 5 -> 5min, 10 -> 30min. Auto-activation schedule support (Pair<LocalTime, LocalTime>). Settings timing: profile changes take effect at next session, feature toggles take effect immediately if no active session.

Biometric authentication via BiometricPrompt API as an alternative to PIN for child profile settings access.

## Context Files

- app/src/main/java/com/scrollshield/data/model/UserProfile.kt
- app/src/main/java/com/scrollshield/data/db/ProfileDao.kt
- app/src/main/java/com/scrollshield/data/preferences/UserPreferencesStore.kt
- app/src/main/java/com/scrollshield/data/model/ClassifiedItem.kt
- app/src/main/java/com/scrollshield/di/AppModule.kt
- work-items/WI-07-profile-management.md

## Target Files (to modify)

- app/src/main/java/com/scrollshield/profile/ProfileManager.kt
- app/src/main/java/com/scrollshield/profile/ChildProfileConfig.kt
- app/src/main/java/com/scrollshield/profile/ProfileSwitcher.kt

## Rules & Constraints

- Use Hilt @Inject constructor for all new classes — follow existing DI patterns
- Use ProfileDao for all database operations — no direct Room queries
- Use UserPreferencesStore for active profile ID and PIN hash storage
- PIN hash must use SHA-256 with device-specific salt from Settings.Secure.ANDROID_ID
- Do not modify any existing files (UserProfile, ProfileDao, UserPreferencesStore, etc.)
- All profile operations must be suspend functions using coroutines
- Child profile blocked categories must match spec exactly (gambling, diet, crypto, explicit ads, alcohol)

## Review Criteria

1. ProfileManager creates a default profile with all required fields populated
2. ChildProfileConfig sets correct pre-selected defaults (blocked categories, classifications, mask, counter, time budget, PIN)
3. ProfileSwitcher requires PIN authentication to switch FROM child profile only
4. PIN is hashed with SHA-256 + device-specific salt (Settings.Secure.ANDROID_ID)
5. Lockout escalation is correctly enforced (3 -> 30s, 5 -> 5min, 10 -> 30min)
6. Active profile switch updates UserPreferencesStore.setActiveProfileId()
7. Auto-activation schedule correctly stores and represents Pair<LocalTime, LocalTime>
8. Settings timing: profile changes deferred to next session, feature toggles immediate if no active session
9. All classes use Hilt @Inject constructor and follow existing DI patterns
10. All database/preference operations are suspend functions with proper coroutine usage

## Implementation Instructions

```
cd /home/devuser/dev-worktree-1
./gradlew :app:compileDebugKotlin
```
