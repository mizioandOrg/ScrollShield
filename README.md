# ScrollShield

**OS-Level Defence Against Algorithmic Manipulation**

ScrollShield is an Android app (API 28+, no root required) that intercepts doomscrolling feeds on TikTok, Instagram Reels, and YouTube Shorts. It detects ads and unwanted content using on-device ML, then gives users visibility and control through two complementary features.

## Features

### Ad Counter (passive)
A persistent floating overlay that tracks every promoted post in real time:
- Live count of ads encountered during a session
- Estimated advertising revenue generated from your attention (per-platform CPM)
- Session summary with brand breakdown, ad-to-content ratio, and export to JSON
- Time budget nudges (configurable per app, 15-120 min)

### Scroll Mask (active, opt-in)
A buffered content proxy that pre-scans 10 items ahead, classifies each one, and auto-skips unwanted content before you ever see it:
- Pre-scan buffer with branded loading animation (~5s at session start)
- 3-tier classification pipeline: signature matching (<5ms) -> label detection (<15ms) -> ML inference (<50ms)
- Lookahead extension scans ahead as you scroll, keeping the buffer full
- Skip flash overlay shows what was filtered ("Skipped: Ad", "Skipped 3 ads")

Both features consume events from the same classification pipeline and can run independently or together.

## How It Works

```
User opens TikTok/Instagram/YouTube
         |
         v
  Feed Interception Service (AccessibilityService)
         |
         v
  Classification Pipeline
    Tier 1: Signature match (SimHash, Hamming distance <= 3)
    Tier 2: Label detection (13 localized patterns: "Sponsored", "Ad", "Anzeige", ...)
    Tier 3: On-device ML (DistilBERT-tiny, float16, TFLite)
         |
         v
  Skip Decision Engine
    -> SHOW / SKIP_AD / SKIP_BLOCKED / SKIP_CHILD / SHOW_LOW_CONF
         |
         v
  Ad Counter overlay  +  Scroll Mask auto-skip
```

### Core Design Principle

The user never leaves the native app. TikTok renders normally -- full video, audio, native UI. ScrollShield is invisible during normal playback. It pre-scans the feed ahead of the user and auto-skips unwanted content before the user reaches it.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| ML Inference | TensorFlow Lite (DistilBERT-tiny, 4L/128H, ~15MB) |
| OCR | ML Kit Text Recognition + Tesseract4Android fallback |
| Database | Room (optional SQLCipher encryption) |
| DI | Hilt |
| Background Work | WorkManager |
| Preferences | DataStore (Proto) + EncryptedSharedPreferences |

## Key Properties

- **Privacy-first**: All classification and preference logic runs on-device. No user content leaves the phone. Zero network calls during core flows.
- **No root required**: Uses Android Accessibility Service APIs and overlay permissions.
- **Works offline**: Classification, skip decisions, session recording, and reporting all work without connectivity. Only signature sync requires WiFi.
- **Child profile**: Restrictive config with tighter blocked categories, lower time budget, mask always on, and PIN-protected settings.
- **Performance**: < 60ms classification per item, < 150MB peak memory, < 3% additional battery drain per hour.

## Project Structure

```
agent-orchestration/
  InputData/
    scrollshield-initial-plan       # Complete technical implementation spec
    RefinedWorkItems/               # 15 agent-sized work items (see below)
    ScrollShield_Demo_Proposal      # Product proposal document
    scrollshield.jsx                # Reference UI prototype
```

## Work Items

The technical spec has been decomposed into 15 self-contained work items, each implementable in a single agent session:

| # | Work Item | Scope |
|---|-----------|-------|
| 01 | Project Scaffolding | Gradle, dependencies, package structure, milestones |
| 02 | Data Models | FeedItem, ClassifiedItem, enums, TypeConverters |
| 03 | Database & DAOs | Room DB, SessionDao, SignatureDao, ProfileDao, DataStore |
| 04 | Utility Classes | SimHash, TextNormaliser, CosineSimilarity |
| 05 | Feed Interception | AccessibilityService, gesture dispatch, per-app compat layer |
| 06 | Classification Pipeline | 3-tier classifier, skip decision engine, dual-OCR |
| 07 | Profile Management | Profile CRUD, child config, PIN auth with lockout |
| 08 | Ad Counter | Overlay UI, session management, time budget nudges |
| 09 | Scroll Mask Pre-Scan | Pre-scan phase, ScanMap, loading overlay |
| 10 | Scroll Mask Live Mode | Live skip, lookahead extension, consecutive skip handling |
| 11 | Onboarding & Settings | 9-screen onboarding, settings UI |
| 12 | Signature Sync | WiFi sync worker, local learning, expiry cleanup |
| 13 | Session Analytics | Weekly/monthly reports, child activity reports |
| 14 | Error Handling | Recovery strategies, diagnostics, low-memory fallback |
| 15 | Testing & ML Pipeline | Unit/integration tests, benchmarks, ML training scaffold |

### Build Order

```
WI-01 (Scaffolding)
  +-- WI-02 (Data Models)
  |     +-- WI-03 (Database)
  |     |     +-- WI-07 (Profiles)
  |     |     |     +-- WI-08 (Ad Counter)
  |     |     |     +-- WI-11 (Onboarding & Settings)
  |     |     +-- WI-12 (Signature Sync)
  |     |     +-- WI-13 (Analytics)
  |     +-- WI-05 (Feed Interception)
  |     +-- WI-09 (Mask Pre-Scan)
  |           +-- WI-10 (Mask Live Mode)
  +-- WI-04 (Utilities)
  |     +-- WI-06 (Classification)
  +-- WI-14 (Error Handling)

WI-15 (Testing) -- after all others
```

## Target Platforms

- **TikTok** (`com.zhiliaoapp.musically`)
- **Instagram Reels** (`com.instagram.android`)
- **YouTube Shorts** (`com.google.android.youtube`)

## Status

Concept & prototype stage. The technical implementation spec is complete and has been split into implementable work items.

## Development Tooling

This repo uses the [Planner-Reviewer-Implementer](https://github.com/anthropics/claude-code-planner-reviewer-implementer) pattern for AI-assisted development. See `agent-orchestration/CLAUDE.md` for orchestration details.
