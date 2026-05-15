package com.scrollshield.perf

import android.os.Debug
import com.scrollshield.util.PerceptualHash
import com.scrollshield.util.SimHash
import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Asserts the steady-state heap delta stays under 150 MB.
 *
 * Canonical metric: Debug.MemoryInfo.totalPss when available (non-zero),
 * otherwise Runtime.totalMemory() heap delta.
 */
@RunWith(RobolectricTestRunner::class)
class MemoryBudgetTest {

    private val budgetKb: Long = 150L * 1024L

    @Test
    fun heapStaysUnderBudget() {
        val runtime = Runtime.getRuntime()
        System.gc()
        val baselineHeap = runtime.totalMemory() - runtime.freeMemory()

        // Touch realistic work to ensure the budget is enforced under load:
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (i in 0 until 50) {
            PerceptualHash.perceptualHash(bmp)
            SimHash.hash("memory budget sample caption $i ".repeat(8))
        }

        val pssKb = readPssKb()
        val canonicalKb = if (pssKb > 0L) {
            pssKb
        } else {
            val usedAfter = runtime.totalMemory() - runtime.freeMemory()
            val deltaBytes = (usedAfter - baselineHeap).coerceAtLeast(0L)
            deltaBytes / 1024L
        }

        assert(canonicalKb < budgetKb) {
            "Memory budget exceeded: ${canonicalKb}KB >= ${budgetKb}KB"
        }
    }

    private fun readPssKb(): Long {
        return try {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            info.totalPss.toLong()
        } catch (_: Throwable) {
            0L
        }
    }
}
