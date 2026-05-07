package com.scrollshield.data.sync

import android.content.Context
import android.graphics.Bitmap
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.scrollshield.data.db.SignatureDao
import com.scrollshield.data.model.AdSignature
import com.scrollshield.util.PerceptualHash
import com.scrollshield.util.SimHash
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncEntryPoint {
    fun signatureDao(): SignatureDao
    fun signatureApiClient(): SignatureApiClient
}

class SignatureSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "signature_sync_periodic"
        private const val PREFS_NAME = "scrollshield_sync_prefs"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val HAMMING_THRESHOLD_TEXT = 3
        private const val HAMMING_THRESHOLD_VISUAL = 8

        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SignatureSyncWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, SyncEntryPoint::class.java
        )
        val signatureDao = entryPoint.signatureDao()
        val apiClient = entryPoint.signatureApiClient()

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSync = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

        val remoteSignatures: List<AdSignature>
        try {
            remoteSignatures = apiClient.fetchSignatures(lastSync)
        } catch (_: Exception) {
            return Result.retry()
        }

        resolveConflictsAndUpsert(signatureDao, remoteSignatures)

        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            .apply()

        return Result.success()
    }

    private suspend fun resolveConflictsAndUpsert(
        signatureDao: SignatureDao,
        remoteSignatures: List<AdSignature>
    ) {
        // Upsert all remote signatures (REPLACE on same ID)
        signatureDao.upsertAll(remoteSignatures)

        // Get all active signatures and find local detections that overlap with synced ones
        val now = System.currentTimeMillis()
        val activeSignatures = signatureDao.getActive(now)
        val localSignatures = activeSignatures.filter { it.source == "local_detection" }
        val syncedSignatures = activeSignatures.filter { it.source == "synced" }

        val expiredLocals = mutableListOf<AdSignature>()

        for (local in localSignatures) {
            for (synced in syncedSignatures) {
                val textMatch = local.simHash != 0L && synced.simHash != 0L &&
                    SimHash.hammingDistance(local.simHash, synced.simHash) <= HAMMING_THRESHOLD_TEXT

                val visualMatch = local.visualHash != null && synced.visualHash != null &&
                    run {
                        try {
                            val localHash = local.visualHash.toLong()
                            val syncedHash = synced.visualHash.toLong()
                            SimHash.hammingDistance(localHash, syncedHash) <= HAMMING_THRESHOLD_VISUAL
                        } catch (_: NumberFormatException) {
                            false
                        }
                    }

                if (textMatch || visualMatch) {
                    // Expire the local signature — synced version takes precedence
                    expiredLocals.add(local.copy(expires = 0L))
                    break
                }
            }
        }

        if (expiredLocals.isNotEmpty()) {
            signatureDao.upsertAll(expiredLocals)
        }
    }
}

class ExpiryCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_CLEANUP_WORK_NAME = "signature_expiry_cleanup"

        fun schedule(context: Context) {
            val cleanupRequest = OneTimeWorkRequestBuilder<ExpiryCleanupWorker>()
                .setInitialDelay(24, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_CLEANUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                cleanupRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, SyncEntryPoint::class.java
        )
        val signatureDao = entryPoint.signatureDao()
        val now = System.currentTimeMillis()
        signatureDao.deleteExpired(now)

        // Reschedule for next cleanup
        schedule(applicationContext)

        return Result.success()
    }
}

/**
 * Creates a local ad signature from a detected ad.
 *
 * Checks for existing signatures with similar hashes before inserting to avoid
 * duplicates. Skips insertion if a synced or local signature already covers the ad.
 *
 * @param signatureDao the DAO for signature database access
 * @param captionText detected ad caption text (may be blank)
 * @param screenCapture optional screenshot bitmap of the detected ad
 * @param advertiser identified advertiser name
 * @param category ad category
 * @param confidence classification confidence score (0.0-1.0)
 */
suspend fun learnFromDetection(
    signatureDao: SignatureDao,
    captionText: String,
    screenCapture: Bitmap?,
    advertiser: String,
    category: String,
    confidence: Float
) {
    val simHash = if (captionText.isNotBlank()) SimHash.hash(captionText) else 0L
    val visualHash = if (screenCapture != null) {
        PerceptualHash.perceptualHash(screenCapture).toString()
    } else {
        null
    }

    val now = System.currentTimeMillis()
    val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000

    // Check for existing signatures that already cover this ad
    val activeSignatures = signatureDao.getActive(now)
    for (existing in activeSignatures) {
        // Check text similarity
        if (simHash != 0L && existing.simHash != 0L &&
            SimHash.hammingDistance(simHash, existing.simHash) <= 3
        ) {
            return // Already covered by existing signature
        }
        // Check visual similarity
        if (visualHash != null && existing.visualHash != null) {
            try {
                val existingVisual = existing.visualHash.toLong()
                val newVisual = visualHash.toLong()
                if (SimHash.hammingDistance(existingVisual, newVisual) <= 8) {
                    return // Already covered by existing signature
                }
            } catch (_: NumberFormatException) {
                // Skip comparison if hash is not numeric
            }
        }
    }

    val signature = AdSignature(
        id = UUID.randomUUID().toString(),
        advertiser = advertiser,
        category = category,
        captionHash = "",
        simHash = simHash,
        confidence = confidence,
        firstSeen = now,
        expires = now + thirtyDaysMillis,
        source = "local_detection",
        locale = java.util.Locale.getDefault().language,
        visualHash = visualHash
    )

    signatureDao.upsertAll(listOf(signature))
}

/**
 * Initializes both signature sync and expiry cleanup workers.
 * Call from Application.onCreate() to ensure both workers run independently.
 */
fun initializeSignatureWorkers(context: Context) {
    SignatureSyncWorker.schedule(context)
    ExpiryCleanupWorker.schedule(context)
}
