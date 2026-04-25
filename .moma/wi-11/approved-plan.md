# Approved Plan — WI-11: Onboarding & Settings UI

Approved at Iteration 4 with Score 10/10.

## Pre-requisite: Non-target file changes (requiring confirmation)

### build.gradle.kts
Add 3 dependencies after existing Hilt dependency:
```kotlin
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.biometric:biometric:1.1.0")
```

### DatabaseModule.kt
Add DAO providers (no duplicates exist — verified):
```kotlin
@Provides
fun provideProfileDao(database: ScrollShieldDatabase): ProfileDao =
    database.profileDao()

@Provides
fun provideSessionDao(database: ScrollShieldDatabase): SessionDao =
    database.sessionDao()

@Provides
fun provideSignatureDao(database: ScrollShieldDatabase): SignatureDao =
    database.signatureDao()
```

### AppModule.kt
**No change** — keep the bare Context provider as-is.

---

## Target File 1: OnboardingScreen.kt

`app/src/main/java/com/scrollshield/ui/onboarding/OnboardingScreen.kt`

### OnboardingViewModel (@HiltViewModel)
- Constructor: `@ApplicationContext context: Context`, `ProfileManager`, `ProfileSwitcher`, `UserPreferencesStore`
- State: `OnboardingState` with `currentStep` (0-8), all form fields (selectedApps, selectedTopics, blocked categories as 6 booleans, timeBudgets, unlimitedApps, adCounterEnabled=true, scrollMaskEnabled=false, wantsChildProfile, childPinDigits, childPinConfirm, accessibilityEnabled, overlayEnabled, mediaProjectionGranted, mediaProjectionDenied)
- TARGET_PACKAGES map: `com.zhiliaoapp.musically` → TikTok, `com.instagram.android` → Instagram, `com.google.android.youtube` → YouTube
- init: detect installed apps via `PackageManager.getInstalledApplications(0)` filtered against TARGET_PACKAGES
- nextStep()/previousStep() with per-step validation
- completeOnboarding(): creates profile via ProfileManager, child profile if needed via createChildProfile() with ChildProfileConfig defaults, sets onboarding complete

### Inline composables
- Step 0 (Welcome): value prop + "Get Started" button
- Step 1 (AppSelection): checkboxes for installed target apps only, ≥1 required
- Step 5 (FeatureToggles): Ad Counter switch (default on), Scroll Mask switch (default off)
- Step 6 (ChildProfilePrompt): "Will children use this phone?" Yes/No, delegates to ChildProfileSetupScreen
- Step 7 (Permissions): 3 permission cards with explanations:
  - Accessibility: navigate to settings, verify on return, re-prompt
  - Overlay: Settings.canDrawOverlays() check
  - MediaProjection: createScreenCaptureIntent(), spec explanation text, denial warning
- Step 8 (Done): confirmation + first-session tip + "Finish" button

### Orchestrator composable: OnboardingScreen(onComplete, viewModel)
- LinearProgressIndicator showing step/9
- when(state.currentStep) dispatches to composables
- Steps 2, 3, 4 delegate to separate files

---

## Target File 2: InterestSelectionScreen.kt

`app/src/main/java/com/scrollshield/ui/onboarding/InterestSelectionScreen.kt`

- LazyVerticalGrid(GridCells.Fixed(2)) with all 20 TopicCategory.entries
- FilterChip per topic showing topic.label
- 3-8 selection constraint enforced
- Counter: "X/8 selected"

---

## Target File 3: BlockedCategoryScreen.kt

`app/src/main/java/com/scrollshield/ui/onboarding/BlockedCategoryScreen.kt`

- 6 Switch toggles: gambling, diet, crypto, political outrage, clickbait, explicit ads
- All off by default
- Individual boolean parameters per category

---

## Target File 4: TimeBudgetScreen.kt

`app/src/main/java/com/scrollshield/ui/onboarding/TimeBudgetScreen.kt`

- Per-app Slider 15-120, step 5, default 30
- Checkbox for Unlimited per app
- Shows only selected apps

---

## Target File 5: ChildProfileSetupScreen.kt

`app/src/main/java/com/scrollshield/ui/onboarding/ChildProfileSetupScreen.kt`

- Info card showing ChildProfileConfig defaults (blocked categories, classifications, mask always on, 15 min budget)
- 4-digit PIN entry + confirm with validation

---

## Target File 6: SettingsScreen.kt

`app/src/main/java/com/scrollshield/ui/settings/SettingsScreen.kt`

### SettingsViewModel (@HiltViewModel)
- Constructor: `@ApplicationContext context: Context`, `ProfileManager`, `ProfileSwitcher`, `UserPreferencesStore`, `ScrollShieldDatabase`
- State: profiles, activeProfile, editable fields, advanced settings, child auth state

### Composable sections:
1. **Profile Switcher** at top
2. **Per-Profile Settings**: interests, blocked categories, time budgets, feature toggles (immediate via profileSwitcher.updateFeatureToggles())
3. **Advanced**: scoring weights (4 sliders), per-platform CPM (3 sliders writing to scrollshield_counter_prefs), pre-scan buffer, extension size (with restart note), status dot thresholds, visual classification toggle, MediaProjection re-grant
4. **Child Profile Management**: BiometricPrompt + PIN auth gate, edit/reset/change PIN
5. **Data**: export CSV/JSON, delete all data (5 stores: Room clearAllTables, DataStore reset, scrollshield_sensitive_prefs, scrollshield_counter_prefs, scrollshield_mp_prefs)

### Timing rules:
- Profile changes → next session via profileManager.updateProfile()
- Feature toggles → immediate via profileSwitcher.updateFeatureToggles()

### CPM storage:
- Writes to scrollshield_counter_prefs SharedPreferences using keys "cpm_TikTok", "cpm_Instagram", "cpm_YouTube" matching AdCounterManager.cpmFor()

### Extension size:
- Stored in scrollshield_counter_prefs under "extension_size"
- UI note: "Takes effect after app restart" (LookaheadExtender uses const val)
