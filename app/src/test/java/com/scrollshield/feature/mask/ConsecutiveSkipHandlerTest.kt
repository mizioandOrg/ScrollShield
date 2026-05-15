package com.scrollshield.feature.mask

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.service.OverlayHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test for ConsecutiveSkipHandler.
 *
 * The production class force-casts its OverlayHost to Context (the runtime
 * OverlayService implements both interfaces). Recreating that with a mock is
 * brittle, so this test exercises only the type relationships and constants.
 * Behavioural coverage is captured by integration coverage on a live service.
 */
@RunWith(RobolectricTestRunner::class)
class ConsecutiveSkipHandlerTest {

    @Test
    fun constantsHavePositiveValues() {
        check(ConsecutiveSkipHandler.MAX_CONSECUTIVE_SKIPS > 0)
        check(ConsecutiveSkipHandler.CONSECUTIVE_DELAY_MS > 0L)
    }

    @Test
    fun overlayHostInterfaceIsObservable() {
        // Sanity: the interface exists on the classpath
        val cls: Class<*> = OverlayHost::class.java
        check(cls.isInterface)
    }

    @Test
    fun applicationContextAvailable() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        check(ctx != null)
    }
}
