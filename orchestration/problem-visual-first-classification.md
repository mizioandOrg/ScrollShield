# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

The current ScrollShield technical spec and work items use a text-based classification pipeline (SimHash signature matching, "Sponsored" label detection, DistilBERT on tokenized text) as the primary detection method. This is fundamentally flawed — source apps (TikTok, Instagram, YouTube) can trivially defeat text-based detection by renaming accessibility node IDs, removing text labels, or obfuscating captions.

Revise the classification pipeline across all affected documents to put visual/image-based analysis first. Screen capture via MediaProjection should be the primary classification path, using an on-device image classification model to detect ad creative patterns, branded overlays, product placements, CTA buttons, and influencer promo patterns from the actual rendered pixels. Text-based methods (SimHash, label matching, DistilBERT) should be repositioned as supplementary fast-path signals that can short-circuit the pipeline when available, but are not relied upon as the primary detection method.

Update the technical spec and all affected work items to reflect this architectural shift. Create new work items (WI-16+) if needed for new capabilities like visual model training, screen capture infrastructure, etc.

## Context Files

- docs/ScrollShield_Demo_Proposal.md
- docs/scrollshield.jsx

## Target Files (to modify)

- docs/technical-spec.md
- work-items/WI-01-project-scaffolding.md
- work-items/WI-02-data-models.md
- work-items/WI-03-database-daos-preferences.md
- work-items/WI-04-utility-classes.md
- work-items/WI-05-feed-interception-service.md
- work-items/WI-06-classification-pipeline.md
- work-items/WI-07-profile-management.md
- work-items/WI-08-ad-counter.md
- work-items/WI-09-scroll-mask-prescan.md
- work-items/WI-10-scroll-mask-live.md
- work-items/WI-11-onboarding-settings.md
- work-items/WI-12-signature-sync.md
- work-items/WI-13-session-analytics.md
- work-items/WI-14-error-handling.md
- work-items/WI-15-testing-ml-pipeline.md

## Rules & Constraints

- Visual-first principle — Screen capture + image classification must be the primary detection path, not a fallback. Text-based methods are supplementary fast-path signals only.
- On-device constraint — All inference must run on-device. No cloud APIs for classification.
- New work items follow existing format — Any new WI files must use the same structure (Source, Goal, Context, Dependencies, Files, Detailed Specification, Acceptance Criteria, Notes).
- Explicit dependency declarations — Every work item (new or modified) must list Hard and Integration dependencies on other WIs.
- Preserve non-classification content — Don't alter spec sections unrelated to the classification pipeline change (e.g., onboarding UI flows, analytics reporting, profile management logic) unless directly impacted.
- Latency budget must be stated — The revised pipeline must include realistic per-tier latency targets for on-device image inference on mid-range Android (Snapdragon 7-series).
- WI numbering continuity — New work items continue from WI-16+. Don't renumber existing WIs.

## Review Criteria

1. Visual-first architecture — The revised pipeline places image/visual classification as the primary tier, not a fallback
2. Text demotion — Text-based tiers (SimHash, label matching, DistilBERT) are clearly repositioned as supplementary/fast-path signals
3. Feasibility — Proposed on-device image model is realistic for mid-range Android (size, latency, memory)
4. Pre-scan integration — The plan addresses how visual classification works during fast-forward pre-scanning (capture timing, latency impact)
5. Training data — The plan outlines what training data is needed and how to acquire it for visual ad detection
6. Completeness — All affected work items and spec sections are updated; no stale text-first references remain
7. Dependency integrity — All new and modified work items have correct, explicit dependency chains
8. No regressions — Unrelated spec sections and work items are not broken or contradicted
9. Evasion resilience — The revised approach is demonstrably harder for source apps to defeat than text-only detection
10. Consistent format — All work items (new and modified) follow the existing WI template structure

## Implementation Instructions

No build commands — this is a document-only task.
