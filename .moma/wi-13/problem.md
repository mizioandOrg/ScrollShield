# Problem Definition

## Orchestration
Max Iterations: 5
Visibility: low
Model: opus
Implement: yes

## Task Description

Implement work-item WI-13 (Session Analytics & Reporting UI) for the ScrollShield2 Android project, located at `/home/devuser/ScrollShield2`. The full specification lives at `/home/devuser/ScrollShield2/work-items/WI-13-session-analytics.md`.

The goal is to build post-session analytics on top of the session recording already produced by WI-08 (Ad Counter): weekly reports, monthly aggregates, child activity reports, data retention cleanup, CSV/JSON export, and the reporting Compose UI.

Key behaviors required:
- Weekly report via `PeriodicWorkRequest` (7 days, anchored Sunday midnight device-local), with notification on completion. Contents: total time per profile, ads detected/skipped, top-5 targeting brands, ad-to-content ratio trend, satisfaction average, and classification method breakdown (Tier 0 text / Tier 1 visual / Tier 2 deep text).
- Monthly aggregates computed at month end and stored as long-lived summary records (totals, per-app breakdown, top-10 brands, tier distribution, visual classifier feedback).
- Child Activity Report (parent-facing, child sessions only, on-demand from Reports screen, separately exportable).
- Data retention: raw sessions deleted after 90 days via a scheduled WorkManager cleanup task using `SessionDao.deleteOlderThan()`; monthly aggregates retained indefinitely.
- Report screen must render <1s with 90 days of data using `@RawQuery` aggregation SQL and an index on `(profileId, startTime)`.
- CSV and JSON export with appropriate headers/schema.

## Context Files

- /home/devuser/ScrollShield2/work-items/WI-13-session-analytics.md
- /home/devuser/ScrollShield2/work-items/WI-02-data-models.md
- /home/devuser/ScrollShield2/work-items/WI-03-database-daos-preferences.md
- /home/devuser/ScrollShield2/work-items/WI-08-ad-counter.md
- /home/devuser/ScrollShield2/work-items/WI-07-profile-management.md

## Target Files (to modify)

- app/src/main/java/com/scrollshield/ui/reports/ReportScreen.kt
- app/src/main/java/com/scrollshield/ui/reports/WeeklyReportCard.kt
- app/src/main/java/com/scrollshield/ui/reports/ChildActivityReport.kt
- New WorkManager workers required by the spec (e.g. weekly report worker, monthly aggregate worker, retention cleanup worker) under an appropriate package (e.g. `com.scrollshield.work` or `com.scrollshield.reports.work`)
- New monthly-aggregate model + DAO + Room entity/migration as needed
- Additive method additions to existing DAOs (e.g. `SessionDao.deleteOlderThan()`, `@RawQuery` aggregation queries) — additive only, no signature changes to existing methods
- Wiring needed to register workers and expose the Reports screen in navigation, kept minimal and confined to `com.scrollshield`

## Rules & Constraints

- Do not modify files outside the target scope listed above. Anything under `com.scrollshield` that is not a reports/analytics/worker concern must remain untouched unless strictly required for wiring, and only additive changes are permitted in that case.
- Do not change signatures of existing DAOs, entities, or domain models from earlier work items — additive changes only.
- Do not add new third-party dependencies; use libraries already declared in the project's Gradle files.
- Follow existing Android/Kotlin conventions in the repo (Compose, Room, WorkManager, package layout under `com.scrollshield`).
- Do not edit files outside `/home/devuser/ScrollShield2/app/src/main/java/com/scrollshield/`, except for any unavoidable Room schema/index migration files that the spec mandates.

## Review Criteria

1. Plan creates `ReportScreen.kt`, `WeeklyReportCard.kt`, and `ChildActivityReport.kt` with a clear Compose structure and a sensible state/ViewModel layer.
2. Weekly report is scheduled via a 7-day `PeriodicWorkRequest` anchored to Sunday midnight in the device timezone, with correct initial-delay computation.
3. Weekly report contents are complete: total time split by profile, ads detected and skipped, top-5 targeting brands, ad-to-content ratio trend, satisfaction average, and classification method breakdown (Tier 0 / Tier 1 / Tier 2).
4. A notification ("Your weekly ScrollShield report is ready") is delivered when the weekly report completes, on a properly registered notification channel.
5. Monthly aggregates are computed at month end, stored as summary records suitable for long-term trend analysis, and include tier distribution and visual classifier accuracy feedback.
6. Child Activity Report filters to child sessions only, is generated on-demand from the Reports screen, and is exportable separately from the weekly report.
7. Data retention deletes raw sessions older than 90 days via a scheduled WorkManager task using `SessionDao.deleteOlderThan()`, while monthly aggregates are retained indefinitely.
8. Report screen meets the <1s render budget for 90 days of data via `@RawQuery` aggregation SQL and a Room index on `(profileId, startTime)`.
9. Both CSV and JSON exports are produced with valid headers/schema and stable, documented field ordering.
10. Changes to existing DAOs/models/entities are strictly additive (no signature breaks), no new third-party dependencies are introduced, and the plan stays within the agreed target scope.

## Implementation Instructions

```
cd /home/devuser/ScrollShield2
./gradlew assembleDebug
```
