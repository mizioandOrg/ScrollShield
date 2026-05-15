package com.scrollshield.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke integration test for the pre-scan flow.
 *
 * The full pre-scan integration depends on MediaProjection, an accessibility
 * service, and a live UI process — none of which are exercisable in a CI
 * environment without an emulator. This placeholder asserts the test target
 * compiles and is discoverable. Real pre-scan behaviour is covered by
 * ScanMapTest (JVM/Robolectric) which exercises the runtime semantics.
 */
@RunWith(AndroidJUnit4::class)
class PreScanIntegrationTest {

    @Test
    fun preScanHarnessIsDiscoverable() {
        // Acts as a discoverability marker; ScanMapTest carries the real logic.
        assert(true)
    }
}
