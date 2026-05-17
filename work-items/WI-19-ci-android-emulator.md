# WI-19: CI Android Emulator Pipeline

## Source
- New work item — extracted from WI-15's instrumented-test scope to give every PR a green/red signal from a real Android runtime before merge.
- Companion to WI-15 (which owns the *content* of the test suite); WI-19 owns the *plumbing* that runs it.

## Goal
Stand up a GitHub Actions workflow that boots an Android emulator on each pull request, builds the debug APK + androidTest APK, runs instrumented tests (`connectedDebugAndroidTest`), and reports a pass/fail check on the PR. Also runs unit tests + lint as fast pre-emulator jobs.

## Context
- The repo currently has **no build CI** — only the two Claude review workflows (`.github/workflows/claude.yml`, `claude-code-review.yml`). PRs (e.g. PR #31 / WI-14) merge without an automated build/test gate.
- The Android project is already configured for instrumented tests: `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` is set in `app/build.gradle.kts`, and `androidx.test.ext:junit` + `espresso-core` are already on the `androidTestImplementation` classpath.
- The `app/src/androidTest/` source directory does **not** yet exist. WI-19 must create a minimal smoke test there so the emulator job has something to run; broader coverage is WI-15's job.
- Build environment is memory-constrained (commit `65f3264` reduced Gradle JVM heap to 1536m / workers to 1). The CI runner has more headroom — the workflow should pass an explicit `-Dorg.gradle.jvmargs` override so CI is not throttled by `gradle.properties`.
- `app/google-services.json` is currently dirty in the working tree and is required for the Firebase plugin to apply. CI must have a working copy — either committed (current state) or restored from a secret. WI-19 should assume the file is checked in and not touch the secrets path.

## Dependencies
- **Hard**: WI-01 (build must already be green locally).
- **Integration**: WI-15 (instrumented test suite will land here over time; WI-19 only seeds a single smoke test).
- **Soft**: none — independent of all feature work items.

## Files to Create / Modify

- `.github/workflows/android-ci.yml` — new workflow with three jobs: `lint-unit`, `assemble`, `instrumented`.
- `app/src/androidTest/java/com/scrollshield/SmokeInstrumentedTest.kt` — minimal smoke test (verifies app package context resolves) so the emulator job has a non-empty suite.
- `README.md` — add a one-line CI badge + a note in the work-items table marking WI-19.

Do **not** modify `gradle.properties` (the 1536m heap is intentional for local low-memory dev). The workflow overrides JVM args inline for CI.

## Detailed Specification

### Workflow trigger
- `pull_request` on any branch targeting `main`.
- `push` on `main` (so the badge stays accurate after merge).
- `workflow_dispatch` for manual runs.
- Concurrency group keyed on `github.ref` with `cancel-in-progress: true` so force-pushed PR branches don't queue stale runs.

### Job 1 — `lint-unit` (fast, always runs)
- Runner: `ubuntu-latest`.
- Steps:
  1. `actions/checkout@v4`.
  2. `actions/setup-java@v4` with `distribution: temurin`, `java-version: 17`.
  3. `gradle/actions/setup-gradle@v4` (handles Gradle caching).
  4. `./gradlew --no-daemon lint testDebugUnitTest -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"`.
  5. Upload `app/build/reports/` as an artifact named `lint-unit-reports` on failure (`if: failure()`).

### Job 2 — `assemble` (builds both APKs for the emulator job)
- Runner: `ubuntu-latest`.
- Needs: `lint-unit` (skip emulator entirely if unit tests fail — saves ~15 min of runner time).
- Steps:
  1. Checkout + setup-java + setup-gradle (same as above).
  2. `./gradlew --no-daemon assembleDebug assembleDebugAndroidTest -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"`.
  3. Upload `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` as a single artifact named `debug-apks`.

### Job 3 — `instrumented` (emulator)
- Runner: `ubuntu-latest` (KVM is available on the standard Ubuntu runner image as of late 2023; no need for the macOS runner anymore).
- Needs: `assemble`.
- Steps:
  1. Checkout + setup-java + setup-gradle.
  2. Enable KVM (required for x86_64 hardware acceleration):
     ```yaml
     - name: Enable KVM
       run: |
         echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
           | sudo tee /etc/udev/rules.d/99-kvm4all.rules
         sudo udevadm control --reload-rules
         sudo udevadm trigger --name-match=kvm
     ```
  3. AVD cache (skips ~5 min of system-image download on warm runs):
     ```yaml
     - uses: actions/cache@v4
       id: avd-cache
       with:
         path: |
           ~/.android/avd/*
           ~/.android/adb*
         key: avd-api33-google-x86_64-v1
     ```
  4. If cache miss, create AVD via `reactivecircus/android-emulator-runner@v2` with `force-avd-creation: false`, `emulator-options: -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none`, and `script: echo "Generated AVD snapshot for caching."`.
  5. Run instrumented tests:
     ```yaml
     - name: Run instrumented tests
       uses: reactivecircus/android-emulator-runner@v2
       with:
         api-level: 33
         target: google_apis
         arch: x86_64
         profile: pixel_6
         force-avd-creation: false
         emulator-options: -no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none
         disable-animations: true
         script: ./gradlew --no-daemon connectedDebugAndroidTest -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"
     ```
  6. Always upload `app/build/reports/androidTests/connected/` as artifact `instrumented-test-report` (`if: always()`).
  7. Always upload `app/build/outputs/androidTest-results/` as artifact `instrumented-test-xml` (`if: always()`).

### API level choice
- **API 33** (Android 13, `google_apis` image, `x86_64`). Rationale:
  - `minSdk` is 28, `targetSdk` is 36 → API 33 is comfortably within the supported range and is the most cached / fastest-booting image on GitHub-hosted runners.
  - API 34+ images on `google_apis` x86_64 still have intermittent boot-loop issues on the emulator-runner action as of early 2026; API 33 is the boring/reliable choice for the first iteration.
  - Later WIs can add a matrix: `api-level: [28, 33, 36]` once the suite is stable. WI-19 keeps it single-axis.

### Smoke instrumented test
Create `app/src/androidTest/java/com/scrollshield/SmokeInstrumentedTest.kt`:

```kotlin
package com.scrollshield

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {
    @Test
    fun appContext_hasExpectedPackageName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.scrollshield", context.packageName)
    }
}
```

Purpose: prove the emulator pipeline boots, installs both APKs, and runs at least one test. WI-15 replaces/extends this with the full suite.

### Hilt + instrumentation note
- `ScrollShieldApp` is annotated with `@HiltAndroidApp`. Tests that need DI will eventually require a `HiltTestApplication` runner — **out of scope for WI-19**. The smoke test above does not use Hilt, so no test runner override is needed yet. Leave a `TODO(WI-15)` comment in the smoke test file noting that `CustomTestRunner` + `HiltTestApplication` will be added when the real suite arrives.

### README badge
Add immediately under the H1 title in `README.md`:

```markdown
[![Android CI](https://github.com/<owner>/<repo>/actions/workflows/android-ci.yml/badge.svg)](https://github.com/<owner>/<repo>/actions/workflows/android-ci.yml)
```

The agent should resolve `<owner>/<repo>` from `git remote get-url origin` rather than hard-coding.

### Branch protection (out of scope)
WI-19 does **not** modify branch protection rules. Once the workflow is green for a few PRs, a human will mark the `instrumented` check as required in the GitHub settings UI. Document this as a follow-up in the WI-19 PR description, not in code.

## Acceptance Criteria
- Pushing to a PR branch triggers `android-ci.yml` automatically.
- `lint-unit` job completes in under 8 minutes on a cold cache.
- `assemble` produces both `app-debug.apk` and `app-debug-androidTest.apk` artifacts.
- `instrumented` job boots an API 33 emulator, installs both APKs, runs `SmokeInstrumentedTest`, and reports green.
- On test failure, the HTML report under `app/build/reports/androidTests/connected/` is uploaded as an artifact and downloadable from the PR's Checks tab.
- The AVD cache hit on a second run cuts the `instrumented` job time by at least 4 minutes versus the cold run.
- README displays the CI status badge.
- No changes to `gradle.properties` (CI overrides JVM args inline; local low-memory config is preserved).

## Notes
- Expected total CI wall-clock on a cold run: **~18–22 minutes** (lint-unit ~5 min, assemble ~5 min, instrumented ~10 min including emulator boot). Warm-cache runs should be ~10–12 minutes.
- The `google-services.json` is currently committed. If it is later moved to a secret, this workflow needs a `Restore google-services.json` step before any Gradle invocation — flag as a future change, do not implement here.
- The `reactivecircus/android-emulator-runner` action is the de-facto standard (used by AndroidX, OkHttp, Square libraries). Pin to `@v2` (major), not a SHA, to keep up with emulator-image fixes.
- Firebase initialization at app startup may emit warnings on the emulator (no Google Play Services for some configurations). The smoke test does not start the Application class's full init path, so this is tolerated. WI-15 will need to address this when adding tests that exercise Hilt-injected Firebase clients.
- If the emulator job proves chronically flaky on shared runners, the fallback is to gate it behind a `ci:emulator` PR label rather than running on every push. Decision deferred to first 5–10 runs of empirical data.
