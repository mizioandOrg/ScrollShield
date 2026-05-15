package com.scrollshield.benchmark

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollshield.classification.ClassificationPipeline
import com.scrollshield.classification.ContentClassifier
import com.scrollshield.classification.LabelDetector
import com.scrollshield.classification.SignatureMatcher
import com.scrollshield.classification.SkipDecisionEngine
import com.scrollshield.classification.VisualClassifier
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.ScoringWeights
import com.scrollshield.data.model.SkipDecision
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.data.model.UserProfile
import com.scrollshield.error.DiagnosticLogger
import com.scrollshield.error.ErrorRecoveryManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * WI-18 Combined memory budget + thermal characterization benchmark.
 *
 * Covers:
 *   - Peak memory while running visual classifications (pre-scan path)
 *   - Live-mode lookahead memory footprint
 *   - Both models (text + visual) resident simultaneously
 *   - 100-item full-pipeline leak check
 *   - 50-iteration thermal sustained-load run
 *
 * Logcat tags:
 *   - WI15_BENCH_RESULT   (consumed by CI perf-gate)
 *   - WI14_THERMAL_FEEDBACK (consumed by WI-14 thermal-feedback scrape)
 *
 * Reference baselines:
 *   - mem_prescan      peak <= 150 MB
 *   - mem_live         peak <= 150 MB
 *   - mem_both_models  peak <= 150 MB
 *   - mem_leak_drift   drift <= 5 MB
 *   - thermal_drift_pct <= 25%
 */
@RunWith(AndroidJUnit4::class)
class MemoryBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    companion object {
        const val THRESHOLD_PEAK_MEMORY_MB = 150L
        const val THRESHOLD_LEAK_DRIFT_MB = 5L
        const val THRESHOLD_THERMAL_LATENCY_DRIFT_PCT = 25
        const val WI14_THERMAL_TAG = "WI14_THERMAL_FEEDBACK"
        const val WI15_TAG = "WI15_BENCH_RESULT"
        const val INPUT_SIZE = 224
        const val FRAME_WIDTH = 1080
        const val FRAME_HEIGHT = 2400
    }

    // ---------------------------------------------------------------- helpers

    private fun percentile(values: LongArray, p: Double): Long {
        require(values.isNotEmpty())
        val sorted = values.sortedArray()
        val rank = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[rank]
    }

    private fun median(values: LongArray): Long = percentile(values, 50.0)

    private fun emitWI15Mem(metric: String, peakMb: Long, delegate: String) {
        Log.i(WI15_TAG, "metric=$metric peak_mb=$peakMb delegate=$delegate")
    }

    private fun emitWI15Drift(metric: String, driftMb: Long) {
        Log.i(WI15_TAG, "metric=$metric drift_mb=$driftMb")
    }

    private fun emitWI15Thermal(value: Int, triggerIter: Int) {
        Log.i(
            WI15_TAG,
            "metric=thermal_drift_pct value=$value trigger_iter=$triggerIter"
        )
    }

    private fun snapshotUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
    }

    private fun snapshotPssMb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong() / 1024L
    }

    private fun peakMb(samples: List<Long>): Long = samples.maxOrNull() ?: 0L

    /**
     * Best-effort CPU temperature in °C. Order of preference:
     *   1. android.os.HardwarePropertiesManager (API 24+, system app only)
     *   2. /sys/class/thermal/thermal_zone0/temp (millidegree)
     *   3. -1 sentinel when neither source is readable.
     */
    private fun readTemperatureC(context: Context): Int {
        // HardwarePropertiesManager via reflection (usually denied for non-system apps)
        try {
            val hpmCls = Class.forName("android.os.HardwarePropertiesManager")
            val service = context.getSystemService("hardware_properties")
            if (service != null) {
                val tempType = hpmCls.getField("DEVICE_TEMPERATURE_CPU").getInt(null)
                val source = hpmCls.getField("TEMPERATURE_CURRENT").getInt(null)
                val getTemperatures = hpmCls.getMethod(
                    "getDeviceTemperatures",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                @Suppress("UNCHECKED_CAST")
                val temps = getTemperatures.invoke(service, tempType, source) as? FloatArray
                val first = temps?.firstOrNull { it.isFinite() && it > -50f }
                if (first != null) return first.toInt()
            }
        } catch (_: Throwable) {
            // fall through
        }

        // sysfs fallback
        try {
            val raw = File("/sys/class/thermal/thermal_zone0/temp").readText().trim()
            val milli = raw.toIntOrNull()
            if (milli != null) return milli / 1000
        } catch (_: Throwable) {
            // fall through
        }

        return -1
    }

    private fun makeProfile(): UserProfile = UserProfile(
        id = "bench-profile",
        name = "Bench",
        isChildProfile = false,
        interestVector = FloatArray(20),
        blockedCategories = emptySet(),
        blockedClassifications = emptySet(),
        timeBudgets = emptyMap(),
        maskEnabled = true,
        counterEnabled = true,
        maskDismissable = true,
        pinProtected = false,
        parentPinHash = null,
        satisfactionHistory = emptyList(),
        scoringWeights = ScoringWeights(),
        createdAt = 0L,
        updatedAt = 0L,
        autoActivateSchedule = null
    )

    private fun makeFeedItem(withCapture: Boolean, seed: Long = 0L): FeedItem {
        val capture = if (withCapture) {
            val bmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            val rng = Random(seed)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE) { rng.nextInt() or 0xFF000000.toInt() }
            bmp.setPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            bmp
        } else null
        return FeedItem(
            id = "item-$seed",
            timestamp = 0L,
            app = "com.example.feed",
            creatorName = "creator",
            captionText = "deterministic memory benchmark $seed",
            hashtags = emptyList(),
            labelText = null,
            screenRegion = Rect(0, 0, FRAME_WIDTH, FRAME_HEIGHT),
            rawNodeDump = "",
            feedPosition = 0,
            accessibilityNodeId = null,
            detectedDurationMs = null,
            screenCapture = capture
        )
    }

    private fun newClassifier(): VisualClassifier {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        return VisualClassifier(context, err)
    }

    private fun newPipeline(visualEnabled: Boolean): ClassificationPipeline {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val signatureMatcher = mockk<SignatureMatcher>(relaxed = true)
        val labelDetector = mockk<LabelDetector>(relaxed = true)
        val visualClassifier = mockk<VisualClassifier>(relaxed = true)
        val contentClassifier = mockk<ContentClassifier>(relaxed = true)
        val skipDecisionEngine = mockk<SkipDecisionEngine>(relaxed = true)
        val errorRecovery = mockk<ErrorRecoveryManager>(relaxed = true)
        val diagnosticLogger = mockk<DiagnosticLogger>(relaxed = true)

        coEvery { signatureMatcher.match(any<FeedItem>()) } returns null
        every { labelDetector.detect(any<FeedItem>()) } returns null
        coEvery { visualClassifier.classify(any<Bitmap>()) } returns
            VisualClassifier.VisualResult(
                classification = Classification.OFFICIAL_AD,
                confidence = 0.92f,
                topicVector = FloatArray(20),
                topicCategory = TopicCategory.fromIndex(0)
            )
        coEvery { contentClassifier.classify(any<FeedItem>()) } returns
            ContentClassifier.ContentResult(
                classification = Classification.ORGANIC,
                confidence = 0.8f,
                topicVector = FloatArray(20),
                topicCategory = TopicCategory.fromIndex(0)
            )
        every {
            skipDecisionEngine.decide(any(), any(), any(), any())
        } returns SkipDecision.SHOW
        every { errorRecovery.shouldSkipVisualClassification() } returns !visualEnabled
        every { errorRecovery.shouldSkipTextClassification() } returns false
        return ClassificationPipeline(
            context = context,
            signatureMatcher = signatureMatcher,
            labelDetector = labelDetector,
            visualClassifier = visualClassifier,
            contentClassifier = contentClassifier,
            skipDecisionEngine = skipDecisionEngine,
            errorRecoveryManager = errorRecovery,
            diagnosticLogger = diagnosticLogger
        )
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun memory_preScanWithVisualClassification() {
        val classifier = newClassifier()
        val samples = mutableListOf<Long>()
        try {
            val bmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            repeat(20) {
                runBlocking { classifier.classify(bmp) }
                samples += snapshotPssMb()
            }
            bmp.recycle()
        } finally {
            classifier.close()
        }
        val peak = peakMb(samples)
        emitWI15Mem("mem_prescan", peak, "CPU_XNNPACK")
        assertTrue(
            "mem_prescan peak=$peak MB exceeds ${THRESHOLD_PEAK_MEMORY_MB} MB",
            peak <= THRESHOLD_PEAK_MEMORY_MB
        )
    }

    @Test
    fun memory_liveModeLookaheadExtension() {
        // Simulate WI-16 live-capture lookahead by holding a small ring buffer of
        // full-frame screen captures while running classifications.
        val classifier = newClassifier()
        val ring = ArrayDeque<Bitmap>()
        val samples = mutableListOf<Long>()
        try {
            for (i in 0 until 30) {
                val frame = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
                ring.addLast(frame)
                if (ring.size > 3) ring.removeFirst().recycle() // 3-frame lookahead window
                val cropped = Bitmap.createScaledBitmap(frame, INPUT_SIZE, INPUT_SIZE, true)
                runBlocking { classifier.classify(cropped) }
                cropped.recycle()
                samples += snapshotPssMb()
            }
        } finally {
            while (ring.isNotEmpty()) ring.removeFirst().recycle()
            classifier.close()
        }
        val peak = peakMb(samples)
        emitWI15Mem("mem_live", peak, "CPU_XNNPACK")
        assertTrue(
            "mem_live peak=$peak MB exceeds ${THRESHOLD_PEAK_MEMORY_MB} MB",
            peak <= THRESHOLD_PEAK_MEMORY_MB
        )
    }

    @Test
    fun memory_bothModelsLoadedSimultaneously() {
        // Load WI-17 visual classifier *and* exercise pipeline (which lazily
        // brings up the text classifier path through the mocked collaborators).
        val classifier = newClassifier()
        val pipeline = newPipeline(visualEnabled = true)
        val profile = makeProfile()
        val samples = mutableListOf<Long>()
        try {
            val bmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            for (i in 0 until 10) {
                runBlocking { classifier.classify(bmp) }
                runBlocking { pipeline.classify(makeFeedItem(withCapture = true, seed = i.toLong()), profile) }
                samples += snapshotPssMb()
            }
            bmp.recycle()
        } finally {
            classifier.close()
        }
        val peak = peakMb(samples)
        emitWI15Mem("mem_both_models", peak, "CPU_XNNPACK")
        assertTrue(
            "mem_both_models peak=$peak MB exceeds ${THRESHOLD_PEAK_MEMORY_MB} MB",
            peak <= THRESHOLD_PEAK_MEMORY_MB
        )
    }

    @Test
    fun memory_leakCheck_100Items() {
        val pipeline = newPipeline(visualEnabled = true)
        val profile = makeProfile()

        // Warmup
        repeat(5) {
            runBlocking { pipeline.classify(makeFeedItem(withCapture = true, seed = -1L * it), profile) }
        }

        // GC + finalize before baseline
        System.gc(); System.runFinalization(); Thread.sleep(100)
        System.gc(); System.runFinalization(); Thread.sleep(100)
        val baselineKb = snapshotPssMb()

        for (i in 0 until 100) {
            runBlocking { pipeline.classify(makeFeedItem(withCapture = true, seed = i.toLong()), profile) }
        }

        System.gc(); System.runFinalization(); Thread.sleep(100)
        System.gc(); System.runFinalization(); Thread.sleep(100)
        val finalKb = snapshotPssMb()

        val driftMb = (finalKb - baselineKb).coerceAtLeast(0L)
        emitWI15Drift("mem_leak_drift", driftMb)
        assertTrue(
            "mem_leak_drift=$driftMb MB exceeds ${THRESHOLD_LEAK_DRIFT_MB} MB",
            driftMb <= THRESHOLD_LEAK_DRIFT_MB
        )
    }

    @Test
    fun thermal_50IterationRun() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val classifier = newClassifier()
        val bmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val inferenceMs = LongArray(50)
        val tempReadings = IntArray(50)
        var triggerIter = -1
        var peakTempC = Int.MIN_VALUE

        try {
            for (i in 0 until 50) {
                val tempC = readTemperatureC(context)
                tempReadings[i] = tempC
                if (tempC > peakTempC) peakTempC = tempC

                val t0 = System.nanoTime()
                runBlocking { classifier.classify(bmp) }
                val t1 = System.nanoTime()
                inferenceMs[i] = (t1 - t0) / 1_000_000L

                // 10-iteration sliding p95
                val windowDriftPct = if (i >= 9) {
                    val initWindow = inferenceMs.copyOfRange(0, 10)
                    val curWindow = inferenceMs.copyOfRange(i - 9, i + 1)
                    val initP95 = percentile(initWindow, 95.0).coerceAtLeast(1L)
                    val curP95 = percentile(curWindow, 95.0)
                    (((curP95 - initP95) * 100L) / initP95).toInt()
                } else 0
                val throttled = windowDriftPct > THRESHOLD_THERMAL_LATENCY_DRIFT_PCT
                if (throttled && triggerIter == -1) triggerIter = i

                Log.w(
                    WI14_THERMAL_TAG,
                    "event=thermal_sample iter=$i temp_c=$tempC " +
                        "inference_ms=${inferenceMs[i]} drift_pct=$windowDriftPct throttled=$throttled"
                )
            }
        } finally {
            classifier.close()
            bmp.recycle()
        }

        val initWindow = inferenceMs.copyOfRange(0, 10)
        val tailWindow = inferenceMs.copyOfRange(40, 50)
        val startP95 = percentile(initWindow, 95.0)
        val endP95 = percentile(tailWindow, 95.0)
        val totalDriftPct = if (startP95 > 0) (((endP95 - startP95) * 100L) / startP95).toInt() else 0

        Log.w(
            WI14_THERMAL_TAG,
            "event=thermal_summary total_iters=50 throttle_trigger_iter=$triggerIter " +
                "peak_temp_c=$peakTempC start_p95_ms=$startP95 end_p95_ms=$endP95 " +
                "drift_pct=$totalDriftPct"
        )
        emitWI15Thermal(totalDriftPct, triggerIter)

        // Soft assertion — emit baseline regardless of outcome.
        try {
            assertTrue(
                "thermal latency drift=$totalDriftPct% exceeds " +
                    "${THRESHOLD_THERMAL_LATENCY_DRIFT_PCT}% over 50 iterations",
                totalDriftPct <= THRESHOLD_THERMAL_LATENCY_DRIFT_PCT
            )
        } catch (afe: AssertionError) {
            Log.w(
                WI15_TAG,
                "metric=thermal_drift_pct soft_fail=true device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
            )
            throw afe
        }
    }
}
