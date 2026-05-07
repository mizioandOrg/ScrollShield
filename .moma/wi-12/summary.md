# MoMa Summary — WI-12: Signature Sync & Local Learning

## Orchestration
- Model: opus
- Iterations: 4 of 5
- Final score: 10/10
- Date: 2026-05-07

## What was implemented

### New files
- `app/src/main/java/com/scrollshield/data/sync/SignatureApiClient.kt` — OkHttp-based HTTP client for delta-syncing ad signatures from remote server with transparent gzip decompression
- `app/src/main/java/com/scrollshield/data/sync/SignatureSyncWorker.kt` — WorkManager periodic sync worker, expiry cleanup worker, local learning function, and initialization entry point

## Key features
1. **Delta sync** — `GET /api/v1/signatures?since={timestamp}&locale={locale}` with upsert for additions
2. **WiFi-only default** — `NetworkType.UNMETERED` constraint on periodic work request
3. **Exponential backoff** — `BackoffPolicy.EXPONENTIAL` with `Result.retry()` on failure
4. **Local learning** — generates `AdSignature` with `simHash` (via `SimHash.hash()`) and `visualHash` (via `PerceptualHash.perceptualHash().toString()`) from detected ads
5. **30-day expiry** — local signatures expire after 30 days, `source = "local_detection"`
6. **Nightly cleanup** — `ExpiryCleanupWorker` as self-rescheduling one-time work request, independently schedulable
7. **Conflict resolution** — synced signatures expire overlapping local_detection entries via hash similarity (simHash hamming <= 3, visualHash hamming <= 8)
8. **Offline-first** — app fully functional without sync; workers are non-blocking
9. **Gzipped JSON parsing** — relies on OkHttp's transparent gzip decompression (no manual GZIPInputStream)
10. **Sync timestamp** — `last_sync_timestamp` updated in SharedPreferences on successful sync

## DI pattern
- Uses `@EntryPoint` / `EntryPointAccessors` pattern (matching `ClassificationPipelineEntryPoint.kt`) since `androidx.hilt:hilt-work` is not in dependencies

## Version bump
- versionCode: 17 -> 18
- versionName: 0.13.0 -> 0.14.0
