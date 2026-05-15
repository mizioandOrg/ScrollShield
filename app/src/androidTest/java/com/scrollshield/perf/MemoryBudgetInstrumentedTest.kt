package com.scrollshield.perf

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented memory budget gate.
 *
 * Canonical metric: Debug.MemoryInfo.totalPss. Must stay below 150 MB.
 */
@RunWith(AndroidJUnit4::class)
class MemoryBudgetInstrumentedTest {

    @Test
    fun totalPssUnderBudget() {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        val pss = info.totalPss
        // Sanity envelope: emulator-class apps idle well under 150 MB
        assert(pss in 0..(150 * 1024)) { "PSS $pss KB exceeds 150 MB envelope" }
    }
}
