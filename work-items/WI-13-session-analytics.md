# WI-13: Session Analytics & Reporting UI

## Source
- Module 7: Session Analytics & Reporting (entire section)
- File Structure: `ui/reports/`

## Goal
Implement weekly reports, monthly aggregates, child activity reports, data retention policy, and the reporting UI.

## Context
Session recording is done by the Ad Counter (WI-08) during sessions. This work item adds post-session analytics: weekly reports, monthly aggregates, child activity reports, data export, and data retention cleanup.

## Dependencies
- **Hard**: WI-02 (SessionRecord), WI-03 (SessionDao), WI-08 (sessions are recorded here)
- **Integration**: WI-07 (profiles for child activity filtering)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/ui/reports/ReportScreen.kt`
- `app/src/main/java/com/scrollshield/ui/reports/WeeklyReportCard.kt`
- `app/src/main/java/com/scrollshield/ui/reports/ChildActivityReport.kt`

## Detailed Specification

### Weekly Report
- Generated via WorkManager 7-day PeriodicWorkRequest, anchored to Sunday midnight in device timezone
- Contents: total time (split by profile), ads detected and skipped, top 5 targeting brands, ad-to-content ratio trend, satisfaction average
- In-app display + JSON export
- Triggers notification: "Your weekly ScrollShield report is ready"

### Monthly Aggregates
- Computed at month end: total sessions, total duration, total ads detected, total ads skipped, average satisfaction, per-app breakdown, top 10 ad brands
- Stored as summary records for long-term trend analysis

### Child Activity Report
- Parent-facing: child sessions only
- Time per app, ads blocked, categories encountered, budget compliance
- On-demand generation from Reports screen
- Exportable separately

### Data Retention
- Raw sessions: 90 days -> then delete (use `SessionDao.deleteOlderThan()`)
- Monthly aggregates retained indefinitely
- Cleanup via scheduled WorkManager task

### Report Screen UI
- Performance: `@RawQuery` with aggregation SQL, index on `(profileId, startTime)` — render < 1s with 90 days data
- Export: CSV and JSON formats
- Valid export format with appropriate headers/schema

## Acceptance Criteria
- Session written on every session end
- Checkpoint survives force-kill
- Weekly report generates correctly on schedule
- Weekly report notification delivered on schedule
- Child report shows child sessions only
- Valid CSV/JSON export
- Report screen renders < 1s with 90 days of data
- Monthly aggregates computed correctly
- Data retention: raw sessions deleted after 90 days, aggregates kept

## Notes
- Open Question 10 (Feed algorithm adaptation): Platforms may adapt feed algorithms if they detect consistent skipping patterns. The reporting UI should track ad frequency per session over time so users can monitor if ad density is increasing.
