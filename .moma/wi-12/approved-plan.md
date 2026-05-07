# Approved Plan — WI-12: Signature Sync & Local Learning

**Approved at:** Iteration 4, Score 10/10

## File 1: `SignatureApiClient.kt`

- `@Singleton` class with `@Inject constructor`
- OkHttpClient with 30s connect / 60s read timeouts, transparent gzip decompression
- `fetchSignatures(sinceTimestamp: Long): List<AdSignature>` — OkHttp GET to `$BASE_URL/api/v1/signatures?since=$sinceTimestamp&locale=$locale`
- Parses JSON via Gson with `TypeToken<List<AdSignature>>`
- Throws `IOException` on network/server errors

## File 2: `SignatureSyncWorker.kt`

### SyncEntryPoint (`@EntryPoint`)
- Provides `signatureDao()` and `signatureApiClient()`

### SignatureSyncWorker (CoroutineWorker)
- `doWork()`: delta sync with conflict resolution, updates `last_sync_timestamp`
- `resolveConflictsAndUpsert()`: upserts remote, expires overlapping local_detection entries by hash similarity
- Companion `schedule()`: 24h periodic, `NetworkType.UNMETERED`, `BackoffPolicy.EXPONENTIAL`

### ExpiryCleanupWorker (CoroutineWorker)
- Independent `schedule()`: OneTimeWorkRequest with 24h delay, self-rescheduling
- `doWork()`: `signatureDao.deleteExpired(now)`, reschedules

### learnFromDetection() (top-level function)
- Generates AdSignature with `SimHash.hash()` and `PerceptualHash.perceptualHash().toString()`
- source="local_detection", expires=now+30 days
- Deduplicates before inserting

### initializeSignatureWorkers() (top-level function)
- Schedules both workers, called from Application.onCreate()
