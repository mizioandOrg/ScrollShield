package com.scrollshield.feature.counter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.model.ClassifiedItem
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.profile.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class StatusColor { GREEN, AMBER, RED }

@Singleton
class AdCounterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDao: SessionDao,
    private val profileManager: ProfileManager
) {

    companion object {
        const val ACTION_AD_CLASSIFIED = "com.scrollshield.feature.counter.AD_CLASSIFIED"
        const val PREFS_NAME = "scrollshield_counter_prefs"
        const val PREFS_OVERLAY = "ad_counter_overlay"

        /**
         * In-process publication contract. Producers (e.g. wrappers around
         * ClassificationPipeline, or tests) call:
         *   AdCounterManager.classifiedItems.tryEmit(item)
         *
         * ClassificationPipeline.kt is read-only and exposes no callback registry,
         * so this MutableSharedFlow is the documented seam.
         */
        val classifiedItems: MutableSharedFlow<ClassifiedItem> = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    data class UiState(
        val sessionId: String = "",
        val currentApp: String = "",
        val sessionStartMs: Long = 0L,
        val adsDetected: Int = 0,
        val adsSkipped: Int = 0,
        val itemsSeen: Int = 0,
        val itemsPreScanned: Int = 0,
        val preScanDurationMs: Long = 0L,
        val brands: Set<String> = emptySet(),
        val categories: Set<String> = emptySet(),
        val tierCounts: IntArray = IntArray(3),
        val classificationCounts: Map<Classification, Int> = emptyMap(),
        val revenue: Float = 0f,
        val maskActive: Boolean = false,
        val isChildProfile: Boolean = false,
        val budgetMinutes: Int = 0,
        val bracket: BudgetState = BudgetState.UNDER
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UiState) return false
            return sessionId == other.sessionId &&
                currentApp == other.currentApp &&
                sessionStartMs == other.sessionStartMs &&
                adsDetected == other.adsDetected &&
                adsSkipped == other.adsSkipped &&
                itemsSeen == other.itemsSeen &&
                itemsPreScanned == other.itemsPreScanned &&
                preScanDurationMs == other.preScanDurationMs &&
                brands == other.brands &&
                categories == other.categories &&
                tierCounts.contentEquals(other.tierCounts) &&
                classificationCounts == other.classificationCounts &&
                revenue == other.revenue &&
                maskActive == other.maskActive &&
                isChildProfile == other.isChildProfile &&
                budgetMinutes == other.budgetMinutes &&
                bracket == other.bracket
        }

        override fun hashCode(): Int {
            var result = sessionId.hashCode()
            result = 31 * result + currentApp.hashCode()
            result = 31 * result + sessionStartMs.hashCode()
            result = 31 * result + adsDetected
            result = 31 * result + adsSkipped
            result = 31 * result + itemsSeen
            result = 31 * result + itemsPreScanned
            result = 31 * result + preScanDurationMs.hashCode()
            result = 31 * result + brands.hashCode()
            result = 31 * result + categories.hashCode()
            result = 31 * result + tierCounts.contentHashCode()
            result = 31 * result + classificationCounts.hashCode()
            result = 31 * result + revenue.hashCode()
            result = 31 * result + maskActive.hashCode()
            result = 31 * result + isChildProfile.hashCode()
            result = 31 * result + budgetMinutes
            result = 31 * result + bracket.hashCode()
            return result
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var checkpointJob: Job? = null
    private var tickerJob: Job? = null
    private var lastBracket: BudgetState = BudgetState.UNDER

    init {
        // Collect classified items — emits drive counter updates.
        scope.launch {
            classifiedItems.collect { item -> onClassified(item) }
        }
    }

    // ---- Lifecycle ----

    fun onAppForeground(app: String) {
        val now = System.currentTimeMillis()
        _uiState.value = UiState(
            sessionId = UUID.randomUUID().toString(),
            currentApp = app,
            sessionStartMs = now
        )
        lastBracket = BudgetState.UNDER

        scope.launch {
            val pid = try {
                profileManager.getActiveProfileId().first()
            } catch (_: Exception) { null }
            val profile = pid?.let {
                try { profileManager.getProfileById(it) } catch (_: Exception) { null }
            }
            val platformKey = pkgToPlatform(app) ?: "default"
            val budget = profile?.timeBudgets?.get(platformKey)
                ?: profile?.timeBudgets?.get("default")
                ?: 0
            _uiState.update {
                it.copy(
                    isChildProfile = profile?.isChildProfile == true,
                    budgetMinutes = budget,
                    maskActive = profile?.maskEnabled == true
                )
            }
        }

        startCheckpointJob()
        startTickerJob()
    }

    fun onAppBackground() {
        checkpointJob?.cancel(); checkpointJob = null
        tickerJob?.cancel(); tickerJob = null
        val state = _uiState.value
        val record = buildSessionRecord(state, endedNormally = true, satisfactionRating = null)
        scope.launch {
            try { sessionDao.upsert(record) } catch (_: Exception) { }
        }
    }

    fun endSession(rating: Int?) {
        if (rating != null) recordSatisfaction(rating)
    }

    fun recordSatisfaction(rating: Int) {
        val state = _uiState.value
        val record = buildSessionRecord(state, endedNormally = true, satisfactionRating = rating)
        scope.launch {
            try { sessionDao.upsert(record) } catch (_: Exception) { }
        }
    }

    /** Called by WI-09 when a detected ad is also skipped by the mask. */
    fun onAdSkipped() {
        _uiState.update { it.copy(adsSkipped = it.adsSkipped + 1) }
    }

    // ---- Counting ----

    private fun onClassified(item: ClassifiedItem) {
        _uiState.update { s ->
            val isAd = item.classification == Classification.OFFICIAL_AD ||
                item.classification == Classification.INFLUENCER_PROMO
            val newItemsSeen = s.itemsSeen + 1
            if (!isAd) {
                s.copy(itemsSeen = newItemsSeen)
            } else {
                val newAdsDetected = s.adsDetected + 1
                val newTier = s.tierCounts.copyOf()
                newTier[item.tier.coerceIn(0, 2)]++
                val brand = item.feedItem.creatorName.takeIf { it.isNotBlank() }
                val newBrands = if (brand != null) s.brands + brand else s.brands
                val newCategories = s.categories + item.topicCategory.label
                val newCounts = s.classificationCounts.toMutableMap().also {
                    it[item.classification] = (it[item.classification] ?: 0) + 1
                }
                val cpm = cpmFor(s.currentApp)
                val newRevenue = newAdsDetected * cpm / 1000f
                s.copy(
                    itemsSeen = newItemsSeen,
                    adsDetected = newAdsDetected,
                    tierCounts = newTier,
                    brands = newBrands,
                    categories = newCategories,
                    classificationCounts = newCounts,
                    revenue = newRevenue
                )
            }
        }
    }

    // ---- Periodic jobs ----

    private fun startCheckpointJob() {
        checkpointJob?.cancel()
        checkpointJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(60_000L)
                val state = _uiState.value
                val record = buildSessionRecord(state, endedNormally = false, satisfactionRating = null)
                try { sessionDao.upsert(record) } catch (_: Exception) { }
            }
        }
    }

    private fun startTickerJob() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val state = _uiState.value
                val elapsedMin = (System.currentTimeMillis() - state.sessionStartMs) / 60_000f
                val bracket = TimeBudgetNudge.evaluate(
                    elapsedMin, state.budgetMinutes.toFloat(), state.isChildProfile
                )
                if (bracket != state.bracket) {
                    _uiState.update { it.copy(bracket = bracket) }
                }
                if (bracket == BudgetState.CHILD_HARD_STOP && lastBracket != BudgetState.CHILD_HARD_STOP) {
                    val intent = Intent("com.scrollshield.action.CHILD_HARD_STOP")
                        .setPackage(context.packageName)
                        .putExtra("platform", pkgToPlatform(state.currentApp))
                    try { context.sendBroadcast(intent) } catch (_: Exception) { }
                }
                lastBracket = bracket
                kotlinx.coroutines.delay(5_000L)
            }
        }
    }

    // ---- Helpers ----

    private fun buildSessionRecord(
        state: UiState,
        endedNormally: Boolean,
        satisfactionRating: Int?
    ): SessionRecord {
        val now = System.currentTimeMillis()
        val durationMin = if (state.sessionStartMs > 0)
            (now - state.sessionStartMs) / 60_000f else 0f
        val pid = try {
            kotlinx.coroutines.runBlocking { profileManager.getActiveProfileId().first() }
        } catch (_: Exception) { null }
        return SessionRecord(
            id = state.sessionId.ifEmpty { UUID.randomUUID().toString() },
            profileId = pid ?: "",
            app = state.currentApp,
            startTime = state.sessionStartMs,
            endTime = now,
            durationMinutes = durationMin,
            itemsSeen = state.itemsSeen,
            itemsPreScanned = state.itemsPreScanned,
            adsDetected = state.adsDetected,
            adsSkipped = state.adsSkipped,
            adBrands = state.brands.toList(),
            adCategories = state.categories.toList(),
            estimatedRevenue = state.revenue,
            satisfactionRating = satisfactionRating,
            maskWasEnabled = state.maskActive,
            preScanDurationMs = state.preScanDurationMs,
            classificationCounts = state.classificationCounts.toMap(),
            endedNormally = endedNormally
        )
    }

    fun pkgToPlatform(pkg: String): String? = when (pkg) {
        "com.zhiliaoapp.musically" -> "TikTok"
        "com.instagram.android" -> "Instagram"
        "com.google.android.youtube" -> "YouTube"
        else -> null
    }

    fun cpmFor(app: String): Float {
        val platform = pkgToPlatform(app) ?: return 10f
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "cpm_$platform"
        if (prefs.contains(key)) return prefs.getFloat(key, 10f)
        return when (platform) {
            "TikTok" -> 10f
            "Instagram" -> 12f
            "YouTube" -> 15f
            else -> 10f
        }
    }

    fun statusDotThresholds(): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val amber = prefs.getInt("dot_amber", 3)
        val red = prefs.getInt("dot_red", 11)
        return amber to red
    }

    fun statusColor(state: UiState): StatusColor {
        val (amber, red) = statusDotThresholds()
        return when {
            state.adsDetected < amber -> StatusColor.GREEN
            state.adsDetected < red -> StatusColor.AMBER
            else -> StatusColor.RED
        }
    }

    // ---- JSON export ----

    fun exportJson(state: UiState): String {
        val now = System.currentTimeMillis()
        val durationMin = if (state.sessionStartMs > 0)
            (now - state.sessionStartMs) / 60_000f else 0f
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("sessionId", state.sessionId)
        obj.put("app", state.currentApp)
        obj.put("startTime", state.sessionStartMs)
        obj.put("endTime", now)
        obj.put("durationMinutes", durationMin.toDouble())
        obj.put("adsDetected", state.adsDetected)
        obj.put("adsSkipped", state.adsSkipped)
        obj.put("brands", JSONArray(state.brands.toList()))
        obj.put("categories", JSONArray(state.categories.toList()))
        obj.put("revenue", state.revenue.toDouble())
        val tier = JSONObject()
        tier.put("tier0", state.tierCounts[0])
        tier.put("tier1", state.tierCounts[1])
        tier.put("tier2", state.tierCounts[2])
        obj.put("tierBreakdown", tier)
        obj.put("tierBreakdownAvailable", true)
        obj.put("itemsSeen", state.itemsSeen)
        obj.put("itemsPreScanned", state.itemsPreScanned)
        val cc = JSONObject()
        for ((k, v) in state.classificationCounts) cc.put(k.name, v)
        obj.put("classificationCounts", cc)
        return obj.toString()
    }

    fun exportJsonForSession(record: SessionRecord): String {
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("sessionId", record.id)
        obj.put("app", record.app)
        obj.put("startTime", record.startTime)
        obj.put("endTime", record.endTime)
        obj.put("durationMinutes", record.durationMinutes.toDouble())
        obj.put("adsDetected", record.adsDetected)
        obj.put("adsSkipped", record.adsSkipped)
        obj.put("brands", JSONArray(record.adBrands))
        obj.put("categories", JSONArray(record.adCategories))
        obj.put("revenue", record.estimatedRevenue.toDouble())
        obj.put("tierBreakdown", JSONObject.NULL)
        obj.put("tierBreakdownAvailable", false)
        obj.put("itemsSeen", record.itemsSeen)
        obj.put("itemsPreScanned", record.itemsPreScanned)
        val cc = JSONObject()
        for ((k, v) in record.classificationCounts) cc.put(k.name, v)
        obj.put("classificationCounts", cc)
        return obj.toString()
    }

    fun exportToDownloads(json: String, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(json.toByteArray()) }
            }
        } catch (_: Exception) { }
    }
}
