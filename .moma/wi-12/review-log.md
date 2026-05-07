# Review Log — WI-12: Signature Sync & Local Learning

## Iteration 1

### Planner Output
Initial plan for two new files: SignatureApiClient (Retrofit/OkHttp, gzip, SyncResponse) and SignatureSyncWorker (periodic sync, ExpiryCleanupWorker, LocalLearning object). All 10 criteria addressed.

### Reviewer Critique
**Score: 9/10**

Issues:
- **Criterion 7 (Conflict resolution)**: `OnConflictStrategy.REPLACE` only works with same primary key. Locally-generated signatures have random UUIDs different from server IDs. Need logic in `doWork()` to remove local_detection signatures when synced signatures with similar hashes arrive.

Passed: Criteria 1, 2, 3, 4, 5, 6, 8, 9, 10

---

## Iteration 2

### Planner Output
Added hash-similarity deduplication during sync: scan local_detection signatures and expire duplicates by upserting with `expires = 0L`. Switched to `@EntryPoint` DI (no `hilt-work` dependency). Switched from Retrofit to raw OkHttp for simplicity.

### Reviewer Critique
**Score: 8/10**

Issues:
- **Criterion 4 (Local learning visualHash)**: `PerceptualHash.perceptualHash(bitmap)` call and Long-to-String conversion not explicitly shown in plan.
- **Criterion 9 (Gzip handling)**: Manual `GZIPInputStream` on top of OkHttp's transparent decompression risks double-decompression bug.

Passed: Criteria 1, 2, 3, 5, 6, 7, 8, 10

---

## Iteration 3

### Planner Output
Fixed gzip: rely on OkHttp's built-in transparent decompression, no manual `GZIPInputStream`. Fixed visualHash: explicitly shows `PerceptualHash.perceptualHash(screenCapture).toString()` with null handling.

### Reviewer Critique
**Score: 9/10**

Issues:
- **Criterion 6 (Nightly cleanup)**: `ExpiryCleanupWorker` scheduling was coupled to `SignatureSyncWorker.schedule()`. If sync scheduling is never called (offline-only user), expired entries may never be cleaned up. Needs independent scheduling.

Passed: Criteria 1, 2, 3, 4, 5, 7, 8, 9, 10

---

## Iteration 4

### Planner Output
ExpiryCleanupWorker now has its own independent `schedule(context)` companion method. Uses `OneTimeWorkRequest` with 24h initial delay that self-reschedules (matching WI-12 spec). Top-level `initializeSignatureWorkers(context)` function provided for `Application.onCreate()`.

### Reviewer Critique
**Score: 10/10**

All 10 criteria passed. Implementation note: use `@EntryPoint` pattern (not `@HiltWorker`) since `androidx.hilt:hilt-work` is not in `build.gradle.kts`. Non-blocking observation only.
