# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: sonnet
Implement: yes

## Task Description

Implement WI-01: Project Scaffolding & Build Configuration for the ScrollShield Android app.

The project already has a partial skeleton. The plan must close the gap between the current state and the WI-01 spec:

**Current state:**
- `settings.gradle.kts` — correct, no changes needed
- `build.gradle.kts` (project-level) — correct, no changes needed
- `app/build.gradle.kts` — wrong namespace/applicationId (`com.scrollshield.app` → `com.scrollshield`), wrong versionName (`1.0` → `1.0-draft`), missing Tesseract4Android dep (`io.github.nicepay:tesseract4android:4.7.0`), missing ProGuard config for release
- `app/src/main/AndroidManifest.xml` — missing all 6 required permissions and accessibility service declaration
- `app/src/main/java/com/scrollshield/app/ScrollShieldApp.kt` — exists but at wrong package path; must be replaced by `com/scrollshield/ScrollShieldApp.kt` with package `com.scrollshield`
- `app/src/main/java/com/scrollshield/app/MainActivity.kt` — package must change from `com.scrollshield.app` to `com.scrollshield` to stay consistent with namespace change
- DI modules — all four missing (AppModule, DatabaseModule, ClassificationModule, MediaProjectionModule)
- `app/src/main/res/xml/accessibility_service_config.xml` — missing
- Package directory stubs — missing

## Context Files

- /home/devuser/ScrollShield/work-items/WI-01-project-scaffolding.md

## Target Files (to modify or create)

- /home/devuser/ScrollShield/app/build.gradle.kts
- /home/devuser/ScrollShield/app/src/main/AndroidManifest.xml
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/ScrollShieldApp.kt
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/app/MainActivity.kt
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/di/AppModule.kt
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/di/DatabaseModule.kt
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/di/ClassificationModule.kt
- /home/devuser/ScrollShield/app/src/main/java/com/scrollshield/di/MediaProjectionModule.kt
- /home/devuser/ScrollShield/app/src/main/res/xml/accessibility_service_config.xml

## Rules & Constraints

- Do not modify `settings.gradle.kts` or `build.gradle.kts` (project-level) — they are already correct
- Do not modify any files outside the Target Files list
- The old `ScrollShieldApp.kt` at `com/scrollshield/app/ScrollShieldApp.kt` must be replaced (overwritten or its content updated) so the class lives at `com/scrollshield/ScrollShieldApp.kt` with package `com.scrollshield`
- The Implementer must create all missing package directories as side-effects of creating files in them (Write tool handles this automatically)
- namespace and applicationId in `app/build.gradle.kts` must both be `"com.scrollshield"` (not `"com.scrollshield.app"`)
- Do not add runtime logic to DI modules beyond what the spec requires — stub implementations are acceptable for dependencies not yet implemented in later WIs
- No container or Docker concerns — we are already inside the containerized dev environment

## Review Criteria

1. `app/build.gradle.kts`: namespace and applicationId are both `"com.scrollshield"`, versionName is `"1.0-draft"`, compileSdk/minSdk/targetSdk are 34/28/34
2. `app/build.gradle.kts`: all required dependencies present with correct minimum versions — Compose BOM 2024.01+, Room 2.6+, Hilt 2.50+, TFLite 2.14+, ML Kit text-recognition 16.0+, Tesseract4Android 4.7.0, WorkManager 2.9+, DataStore, Coroutines, Retrofit/OkHttp, test libs
3. `app/build.gradle.kts`: release build type has `isMinifyEnabled = true` with a proguard rules file reference
4. `AndroidManifest.xml`: all 6 permissions declared — SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, INTERNET, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED
5. `AndroidManifest.xml`: accessibility service declared with correct `android:name`, `android:permission`, and `android:resource` pointing to `@xml/accessibility_service_config`
6. `accessibility_service_config.xml`: matches spec exactly — correct event types, flags, package names (TikTok/Instagram/YouTube), notificationTimeout=100, canRetrieveWindowContent=true, canPerformGestures=true
7. `ScrollShieldApp.kt` at path `com/scrollshield/ScrollShieldApp.kt` with package `com.scrollshield`, `@HiltAndroidApp`, extends `Application`
8. `MainActivity.kt` updated to package `com.scrollshield` (consistent with namespace change), annotated with `@AndroidEntryPoint`
9. All 4 DI modules exist with correct `@Module`, `@InstallIn`, and at minimum stub `@Provides` methods matching their responsibilities (AppModule: context + dispatchers; DatabaseModule: Room DB + DAOs placeholder; ClassificationModule: TFLite + pipeline stubs; MediaProjectionModule: MediaProjectionManager)
10. Old `ScrollShieldApp.kt` at `com/scrollshield/app/ScrollShieldApp.kt` is replaced/overwritten so the class no longer lives in the `app` sub-package (prevent duplicate class conflict)

## Implementation Instructions

```
cd /home/devuser/ScrollShield
./gradlew assembleDebug --no-daemon 2>&1 | tail -20
```
