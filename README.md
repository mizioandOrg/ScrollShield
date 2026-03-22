# ScrollShield

**OS-Level Defence Against Algorithmic Manipulation**

ScrollShield is an Android app (API 28+, no root required) that intercepts doomscrolling feeds on TikTok, Instagram Reels, and YouTube Shorts. It detects ads and unwanted content using on-device visual classification, then gives users visibility and control through two complementary features.

## Features

### Ad Counter (passive)
A persistent floating overlay that tracks every promoted post in real time:
- Live count of ads encountered during a session
- Estimated advertising revenue generated from your attention (per-platform CPM)
- Session summary with brand breakdown, ad-to-content ratio, and export to JSON
- Time budget nudges (configurable per app, 15-120 min)

### Scroll Mask (active, opt-in)
A buffered content proxy that pre-scans 10 items ahead, classifies each one, and auto-skips unwanted content before you ever see it:
- Pre-scan buffer with branded loading animation (~5.5s at session start)
- Visual-first classification pipeline: screen capture + image ML as primary detection
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
  Screen Capture Service (MediaProjection)
         |
         v
  Classification Pipeline (Visual-First)
    Tier 0: Text fast-path — SimHash + label detection  [supplementary]
    Tier 1: Visual classification — MobileNetV3-Small   [PRIMARY]
    Tier 2: Deep text analysis — DistilBERT-tiny        [supplementary]
         |
         v
  Skip Decision Engine
    -> SHOW / SKIP_AD / SKIP_BLOCKED / SKIP_CHILD / SHOW_LOW_CONF
         |
         v
  Ad Counter overlay  +  Scroll Mask auto-skip
```

### Why Visual-First?

Text-based detection (reading "Sponsored" labels, matching captions) is easily defeated — source apps can rename accessibility node IDs, remove text labels, or obfuscate captions. Visual classification operates on the actual rendered pixels via MediaProjection screen capture. Apps cannot hide ad content from visual detection without also hiding it from users, which defeats the purpose of the ad.

### Core Design Principle

The user never leaves the native app. TikTok renders normally -- full video, audio, native UI. ScrollShield is invisible during normal playback. It pre-scans the feed ahead of the user and auto-skips unwanted content before the user reaches it.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Visual Classification | TensorFlow Lite (MobileNetV3-Small, ~3.4MB, int8) |
| Text Classification | TensorFlow Lite (DistilBERT-tiny, 4L/128H, ~15MB) |
| Screen Capture | MediaProjection API + ImageReader |
| OCR | ML Kit Text Recognition + Tesseract4Android fallback |
| Database | Room (optional SQLCipher encryption) |
| DI | Hilt |
| Background Work | WorkManager |
| Preferences | DataStore (Proto) + EncryptedSharedPreferences |

## Key Properties

- **Privacy-first**: All classification runs on-device. Screen captures are held in memory only during classification (< 100ms), never written to disk or transmitted. Zero network calls during core flows.
- **Evasion-resilient**: Visual classification detects ad patterns from rendered pixels — harder to defeat than text-based approaches.
- **No root required**: Uses Android Accessibility Service APIs, MediaProjection, and overlay permissions.
- **Works offline**: Classification, skip decisions, session recording, and reporting all work without connectivity. Only signature sync requires WiFi.
- **Child profile**: Restrictive config with tighter blocked categories, lower time budget, mask always on, and PIN-protected settings.
- **Performance**: < 100ms classification per item (< 85ms typical), < 150MB peak memory, < 3% additional battery drain per hour.

## Project Structure

```
docs/
  technical-spec.md              # Complete technical implementation spec
  technical-spec-user-draft.md   # Original user draft
  ScrollShield_Demo_Proposal.md  # Product proposal document
  scrollshield.jsx               # Reference UI prototype
work-items/                      # 18 agent-sized work items (see below)
orchestration/                   # PRI process artifacts and checkpoints
```

## Work Items

The technical spec has been decomposed into 18 self-contained work items, each implementable in a single agent session:

| # | Work Item | Scope |
|---|-----------|-------|
| 01 | Project Scaffolding | Gradle, dependencies, package structure, milestones |
| 02 | Data Models | FeedItem, ClassifiedItem, enums, TypeConverters |
| 03 | Database & DAOs | Room DB, SessionDao, SignatureDao, ProfileDao, DataStore |
| 04 | Utility Classes | SimHash, TextNormaliser, CosineSimilarity, PerceptualHash |
| 05 | Feed Interception | AccessibilityService, gesture dispatch, screen capture coordination |
| 06 | Classification Pipeline | Visual-first classifier (Tier 0/1/2), skip decision engine |
| 07 | Profile Management | Profile CRUD, child config, PIN auth with lockout |
| 08 | Ad Counter | Overlay UI, session management, time budget nudges |
| 09 | Scroll Mask Pre-Scan | Pre-scan phase, ScanMap, loading overlay |
| 10 | Scroll Mask Live Mode | Live skip, lookahead extension, consecutive skip handling |
| 11 | Onboarding & Settings | 9-screen onboarding, MediaProjection permission, settings UI |
| 12 | Signature Sync | WiFi sync worker, local learning, visual signatures, expiry cleanup |
| 13 | Session Analytics | Weekly/monthly reports, classification method breakdown |
| 14 | Error Handling | Recovery strategies, MediaProjection revocation, diagnostics |
| 15 | Testing & ML Pipeline | Unit/integration tests, benchmarks, ML training scaffold |
| 16 | Screen Capture Service | MediaProjection infrastructure, frame capture, privacy controls |
| 17 | Visual Model Training | Training dataset, MobileNetV3-Small fine-tuning, TFLite export |
| 18 | Visual Signature Matching | On-device benchmark, perceptual hash matching, optimization |

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
  +-- WI-04 (Utilities + PerceptualHash)
  |     +-- WI-06 (Classification — Visual-First)
  +-- WI-14 (Error Handling)
  +-- WI-16 (Screen Capture Service)

WI-15 (Testing) -- after all others
WI-17 (Visual Model Training) -- after WI-15
WI-18 (Visual Signature Matching) -- after WI-17 + WI-16
```

## Target Platforms

- **TikTok** (`com.zhiliaoapp.musically`)
- **Instagram Reels** (`com.instagram.android`)
- **YouTube Shorts** (`com.google.android.youtube`)

## Status

Concept & prototype stage. The technical implementation spec is complete and has been split into implementable work items.

## Development Tooling

This repo uses the [Planner-Reviewer-Implementer](https://github.com/mizioandorg/claude-planner-reviewer-implementer) pattern for AI-assisted development. See `claude-planner-reviewer-implementer/agent-orchestration/CLAUDE.md` for orchestration details.
