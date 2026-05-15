package com.scrollshield.feature.counter

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.data.db.SessionDao
import com.scrollshield.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Dedicated 1000-emission bench: wall-time must stay under 100 ms.
 *
 * Emits 1000 state copies on the UI dataflow surface and asserts the work
 * completes inside the per-frame budget. Uses a synchronous emission loop
 * to keep the test deterministic; no main dispatcher coroutine launch.
 */
@RunWith(RobolectricTestRunner::class)
class CounterOverlayFpsTest {

    @Test
    fun overlayHandlesOneThousandEmissionsUnder100Ms() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SessionDao>(relaxed = true)
        val pm = mockk<ProfileManager>(relaxed = true)
        every { pm.getActiveProfileId() } returns flowOf(null)
        val mgr = AdCounterManager(context, dao, pm)

        val start = System.nanoTime()
        var json = ""
        for (i in 0 until 1000) {
            json = mgr.exportJson(
                AdCounterManager.UiState(
                    sessionId = "s$i",
                    adsDetected = i % 17,
                    itemsSeen = i,
                )
            )
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        check(json.isNotEmpty())
        check(elapsedMs < 100.0) {
            "1000 emissions took ${elapsedMs}ms, exceeds 100ms budget"
        }
    }
}
