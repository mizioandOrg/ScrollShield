# WI-01: Project Scaffolding & Build Configuration

## Source
- Project section (lines 1-11)
- Technology Stack table
- File Structure
- Implementation Order (milestone mapping)

## Goal
Create the Android project skeleton with all dependencies, modules, and build configuration so that subsequent work items have a compilable base.

## Context
ScrollShield is a single-APK Android app targeting API 28+. Stack: Kotlin, Jetpack Compose, TensorFlow Lite, Room, Hilt. The project uses a single `app` module with package `com.scrollshield`.

## Dependencies
- **Hard**: None — this is the first work item.
- **Integration**: None.

## Files to Create / Modify

- `settings.gradle.kts`
- `build.gradle.kts` (project-level)
- `app/build.gradle.kts` (module-level)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/scrollshield/ScrollShieldApp.kt`
- `app/src/main/java/com/scrollshield/di/AppModule.kt`
- `app/src/main/java/com/scrollshield/di/DatabaseModule.kt`
- `app/src/main/java/com/scrollshield/di/ClassificationModule.kt`
- `app/src/main/res/xml/accessibility_service_config.xml`
- Directory stubs for all packages in the file structure

## Detailed Specification

### `settings.gradle.kts`
```kotlin
rootProject.name = "ScrollShield"
include(":app")
```

### `build.gradle.kts` (project-level)
- Kotlin 1.9+
- Android Gradle Plugin 8.2+
- Hilt Gradle Plugin

### `app/build.gradle.kts`
```
android {
    namespace = "com.scrollshield"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.scrollshield"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-draft"
    }
}
```

Dependencies (with minimum versions from spec):
| Dependency | Min Version |
|---|---|
| Jetpack Compose BOM | 2024.01+ |
| Room | 2.6+ |
| Hilt | 2.50+ |
| TensorFlow Lite | 2.14+ |
| ML Kit Text Recognition | 16.0+ |
| Tesseract4Android | 4.7.0 (`io.github.nicepay:tesseract4android:4.7.0`) |
| WorkManager | 2.9+ |
| DataStore (Proto) | latest stable |
| JUnit5, Espresso, Robolectric | latest stable |
| Kotlin Coroutines + Flow | latest stable |

### `accessibility_service_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:packageNames="com.zhiliaoapp.musically,com.instagram.android,com.google.android.youtube"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true" />
```

### `ScrollShieldApp.kt`
```kotlin
@HiltAndroidApp
class ScrollShieldApp : Application()
```

### DI modules
- `AppModule.kt`: provide application context, coroutine dispatchers
- `DatabaseModule.kt`: provide Room database singleton, all DAOs
- `ClassificationModule.kt`: provide TFLite interpreter, pipeline components

### AndroidManifest.xml
Declare permissions:
- `SYSTEM_ALERT_WINDOW` (overlay)
- `FOREGROUND_SERVICE` (MediaProjection fallback)
- `INTERNET` (signature sync only)
- `ACCESS_NETWORK_STATE` (WiFi check for sync)
- `RECEIVE_BOOT_COMPLETED` (WorkManager)

Declare accessibility service with config reference.

### Milestones (from original spec Implementation Order)

Map work items to delivery milestones:
- **MVP** (original steps 1-5): WI-01, WI-02, WI-03, WI-04, WI-05, WI-06 (Tier 2 + Skip Decision), WI-08
- **V1** (original steps 1-9): MVP + WI-06 (full pipeline), WI-07, WI-09, WI-10, WI-11
- **V1.1** (original steps 10-12): V1 + WI-12, WI-13, WI-14, WI-15

## Acceptance Criteria
- Project compiles with `./gradlew assembleDebug`
- All dependencies resolve
- Empty app launches on API 28+ emulator
- All package directories exist
- Accessibility service config is valid XML

## Notes
- Open Question 7 (Distribution): Google Play Store may reject AccessibilityService usage. Plan for sideload APK as primary, F-Droid as secondary. Prepare Play Store appeal emphasising parental control use case. Maintain F-Droid and direct APK download as primary distribution from day one.
- ProGuard rules should strip HTTP client classes outside the signature sync module (from Security & Privacy section) — configure in this work item but enforcement is validated in WI-14.
