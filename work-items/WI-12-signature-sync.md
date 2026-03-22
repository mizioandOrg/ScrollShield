# WI-12: Signature Sync & Local Learning

## Source
- Module 6: Signature Database & Sync (entire section)
- File Structure: `data/sync/`

## Goal
Implement signature sync with remote server, local learning (generating signatures from detected ads), and expiry cleanup.

## Context
The ad signature database powers Tier 1 matching. It can be populated via server sync and via local detection. Sync is optional — the app is fully functional without it. WiFi-only by default.

## Dependencies
- **Hard**: WI-02 (AdSignature model including visualHash), WI-03 (SignatureDao including getActiveVisualHashes), WI-06 (Tier 0a uses signatures — text and visual, local learning generates them)
- **Integration**: WI-04 (SimHash for text signatures, PerceptualHash for visual signatures)

## Files to Create / Modify

- `app/src/main/java/com/scrollshield/data/sync/SignatureSyncWorker.kt`
- `app/src/main/java/com/scrollshield/data/sync/SignatureApiClient.kt`

## Detailed Specification

### Sync
- Endpoint: `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- HTTPS REST, delta sync on `last_sync_timestamp`
- Schedule: every 24h on WiFi, or manual trigger from Settings
- Payload: Gzipped JSON
- Synced signatures may include `visualHash` field for visual signature matching
- Conflict resolution: prefer `synced` source over `local_detection` when signatures overlap
- Database migration: Room auto-migrations with server schema version check
- Fully functional without sync — app works entirely offline

### SignatureSyncWorker
- WorkManager periodic work request (24h)
- Constraint: `NetworkType.UNMETERED` (WiFi-only default)
- On success: update `last_sync_timestamp` in preferences
- On failure: retry with exponential backoff

### SignatureApiClient
- Retrofit/OkHttp-based HTTP client
- `GET /api/v1/signatures?since={timestamp}&locale={locale}`
- Parse gzipped JSON response into `List<AdSignature>`
- Handle errors: network failure, malformed response, server error

### Local Learning
- Ads detected by Tier 0b, Tier 1, or Tier 2 that are not in the signature cache → generate `AdSignature`:
  - `source = "local_detection"`
  - `simHash` computed via SimHash utility (from caption text, if available)
  - `visualHash` computed via PerceptualHash utility (from screenCapture, if available)
  - `confidence` from the classification result
  - `expires` = current time + 30 days
- Stored locally, used in subsequent Tier 0a lookups (both text and visual signatures)
- Not shared unless user opts in (future feature)

### Expiry Cleanup
- Nightly WorkManager task removes expired signatures (`expires < currentTime`)
- Runs as a one-time work request rescheduled daily

## Acceptance Criteria
- Lookup < 5ms for 100K entries (Tier 1 performance)
- Delta sync adds/removes signatures correctly
- **WiFi-only default** (uses `NetworkType.UNMETERED` constraint)
- Local detections stored and used in Tier 1
- Expired signatures cleaned up nightly
- **Database < 50MB** (target: 50K-100K entries)
- Conflict resolution prefers synced over local_detection
- App fully functional without any sync
- Local learning generates visual signatures (visualHash) alongside text signatures
- Visual signatures used in subsequent Tier 0a lookups

## Notes
- ProGuard rules should strip HTTP client classes outside this module (enforced in WI-01/WI-14).
