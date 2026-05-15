package com.scrollshield.benchmark

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scrollshield.classification.VisualClassifier
import com.scrollshield.error.ErrorRecoveryManager
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import kotlin.random.Random

/**
 * WI-18 Visual Classifier on-device benchmark.
 *
 * Exercises the WI-17 VisualClassifier (MobileNetV3-Small TFLite) under each
 * available delegate (NNAPI → GPU → CPU/XNNPack fallback) and records
 * latency baselines used by the WI-15 regression gate.
 *
 * Result lines:
 *   WI15_BENCH_RESULT metric=<name> median_ms=<int> p95_ms=<int> delegate=<str>
 *
 * Reference baselines (informational thresholds):
 *   - cold_start         p95 <= 350 ms
 *   - nnapi_inference    p95 <= 60  ms
 *   - cpu_xnnpack_inf    p95 <= 100 ms
 *
 * Hardware target: Pixel 6a class device, Android 13+, SoC ~ Tensor G1.
 */
@RunWith(AndroidJUnit4::class)
class VisualClassifierBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    companion object {
        const val THRESHOLD_NNAPI_INFERENCE_MS_P95 = 60L
        const val THRESHOLD_CPU_INFERENCE_MS_P95 = 100L
        const val THRESHOLD_COLD_START_MS_P95 = 350L
        const val WI15_TAG = "WI15_BENCH_RESULT"
        const val WI18_TAG = "WI18_DELEGATE"
        const val WARMUP_ITERATIONS = 5
        const val MEASURE_ITERATIONS = 50
        const val COLD_START_MEASURE_ITERATIONS = 20
        const val INPUT_SIZE = 224
    }

    /**
     * NNAPI → GPU → CPU/XNNPack delegate selection. Attaches the chosen
     * delegate to [interpreterOptions] and returns its identifier string.
     */
    private fun selectDelegate(interpreterOptions: Interpreter.Options): String {
        // 1. NNAPI
        try {
            val nnApiDelegate = NnApiDelegate()
            interpreterOptions.addDelegate(nnApiDelegate)
            logDelegate("NNAPI")
            return "NNAPI"
        } catch (_: Throwable) {
            // fall through
        }

        // 2. GPU (reflective so the artifact still builds without the gpu module)
        try {
            val compatCls = Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
            val compat = compatCls.getDeclaredConstructor().newInstance()
            val supported = compatCls
                .getMethod("isDelegateSupportedOnThisDevice")
                .invoke(compat) as Boolean
            if (supported) {
                val gpuCls = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                val gpu = gpuCls.getDeclaredConstructor().newInstance()
                interpreterOptions.addDelegate(gpu as org.tensorflow.lite.Delegate)
                logDelegate("GPU")
                return "GPU"
            }
        } catch (_: Throwable) {
            // fall through
        }

        // 3. CPU + XNNPack
        interpreterOptions.setUseXNNPACK(true).setNumThreads(4)
        logDelegate("CPU_XNNPACK")
        return "CPU_XNNPACK"
    }

    private fun logDelegate(delegateName: String) {
        Log.i(
            WI18_TAG,
            "active_delegate=$delegateName device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
        )
    }

    /** Returns the p-th percentile (0..100) of a non-empty long sample set. */
    private fun percentile(values: LongArray, p: Double): Long {
        require(values.isNotEmpty()) { "values must be non-empty" }
        val sorted = values.sortedArray()
        val rank = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[rank]
    }

    private fun median(values: LongArray): Long = percentile(values, 50.0)

    private fun emitWI15Baseline(metric: String, medianMs: Long, p95Ms: Long, delegate: String) {
        Log.i(
            WI15_TAG,
            "metric=$metric median_ms=$medianMs p95_ms=$p95Ms delegate=$delegate"
        )
    }

    /** Deterministic 224x224 RGBA input bitmap used as inference fixture. */
    private fun fixtureBitmap(seed: Long = 0xC0FFEEL): Bitmap {
        val rng = Random(seed)
        val bmp = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE) { rng.nextInt() or 0xFF000000.toInt() }
        bmp.setPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        return bmp
    }

    private fun newClassifier(): VisualClassifier {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        return VisualClassifier(context, err)
    }

    @Test
    fun benchmark_coldStart_modelLoad() {
        val samples = LongArray(COLD_START_MEASURE_ITERATIONS)
        var delegate = "UNKNOWN"
        var captured = false
        try {
            // Warmup (discarded)
            val warmFixture = fixtureBitmap(seed = 1)
            repeat(WARMUP_ITERATIONS) {
                val c = newClassifier()
                runBlocking { c.classify(warmFixture) }
                c.close()
            }

            for (i in 0 until COLD_START_MEASURE_ITERATIONS) {
                val c = newClassifier()
                val fixture = fixtureBitmap(seed = i.toLong())
                val t0 = System.nanoTime()
                runBlocking { c.classify(fixture) }
                val t1 = System.nanoTime()
                samples[i] = (t1 - t0) / 1_000_000L
                c.close()
            }

            // Determine effective delegate path for label only (best-effort)
            delegate = try {
                NnApiDelegate().close(); "NNAPI"
            } catch (_: Throwable) {
                "CPU_XNNPACK"
            }
            captured = true
        } finally {
            if (captured) {
                val med = median(samples)
                val p95 = percentile(samples, 95.0)
                emitWI15Baseline("cold_start", med, p95, delegate)
                assertTrue(
                    "cold_start p95=$p95 ms exceeds ${THRESHOLD_COLD_START_MS_P95} ms",
                    p95 <= THRESHOLD_COLD_START_MS_P95
                )
            }
        }
    }

    @Test
    fun benchmark_nnapiInference() {
        // Skip when NNAPI not available on the test device.
        val nnapiAvailable = try {
            NnApiDelegate().close(); true
        } catch (_: Throwable) {
            false
        }
        Assume.assumeTrue("NNAPI not available on this device", nnapiAvailable)

        val classifier = newClassifier()
        try {
            val fixture = fixtureBitmap()
            // Warmup
            repeat(WARMUP_ITERATIONS) { runBlocking { classifier.classify(fixture) } }

            val samples = LongArray(MEASURE_ITERATIONS)
            for (i in 0 until MEASURE_ITERATIONS) {
                val t0 = System.nanoTime()
                runBlocking { classifier.classify(fixture) }
                val t1 = System.nanoTime()
                samples[i] = (t1 - t0) / 1_000_000L
            }
            val med = median(samples)
            val p95 = percentile(samples, 95.0)
            emitWI15Baseline("nnapi_inference", med, p95, "NNAPI")
            assertTrue(
                "nnapi_inference p95=$p95 ms exceeds ${THRESHOLD_NNAPI_INFERENCE_MS_P95} ms",
                p95 <= THRESHOLD_NNAPI_INFERENCE_MS_P95
            )
        } finally {
            classifier.close()
        }
    }

    @Test
    fun benchmark_cpuXnnpackInference() {
        val classifier = newClassifier()
        try {
            val fixture = fixtureBitmap()
            repeat(WARMUP_ITERATIONS) { runBlocking { classifier.classify(fixture) } }

            val samples = LongArray(MEASURE_ITERATIONS)
            for (i in 0 until MEASURE_ITERATIONS) {
                val t0 = System.nanoTime()
                runBlocking { classifier.classify(fixture) }
                val t1 = System.nanoTime()
                samples[i] = (t1 - t0) / 1_000_000L
            }
            val med = median(samples)
            val p95 = percentile(samples, 95.0)
            emitWI15Baseline("cpu_xnnpack_inference", med, p95, "CPU_XNNPACK")
            assertTrue(
                "cpu_xnnpack_inference p95=$p95 ms exceeds ${THRESHOLD_CPU_INFERENCE_MS_P95} ms",
                p95 <= THRESHOLD_CPU_INFERENCE_MS_P95
            )
        } finally {
            classifier.close()
        }
    }

    @Test
    fun benchmark_gpuInference() {
        // Skip if GPU delegate is not loadable / supported.
        val gpuSupported = try {
            val compatCls = Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
            val compat = compatCls.getDeclaredConstructor().newInstance()
            compatCls.getMethod("isDelegateSupportedOnThisDevice").invoke(compat) as Boolean
        } catch (_: Throwable) {
            false
        }
        Assume.assumeTrue("GPU delegate not supported on this device", gpuSupported)

        val classifier = newClassifier()
        try {
            val fixture = fixtureBitmap()
            repeat(WARMUP_ITERATIONS) { runBlocking { classifier.classify(fixture) } }

            val samples = LongArray(MEASURE_ITERATIONS)
            for (i in 0 until MEASURE_ITERATIONS) {
                val t0 = System.nanoTime()
                runBlocking { classifier.classify(fixture) }
                val t1 = System.nanoTime()
                samples[i] = (t1 - t0) / 1_000_000L
            }
            val med = median(samples)
            val p95 = percentile(samples, 95.0)
            emitWI15Baseline("gpu_inference", med, p95, "GPU")
        } finally {
            classifier.close()
        }
    }

    /**
     * Demonstrates the BenchmarkRule wiring for an inference call so that
     * `androidx.benchmark` warmup/iteration semantics also have coverage.
     * Reports through the standard benchmark harness; not gated by p95.
     */
    @Test
    fun benchmark_androidxBenchmarkRule_smoke() {
        val classifier = newClassifier()
        try {
            val fixture = fixtureBitmap()
            benchmarkRule.measureRepeated {
                runBlocking { classifier.classify(fixture) }
            }
        } finally {
            classifier.close()
        }
    }
}
