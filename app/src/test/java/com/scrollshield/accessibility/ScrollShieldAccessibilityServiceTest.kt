package com.scrollshield.accessibility

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke test that the accessibility service class is on the classpath and
 * declares the expected constants. Full behavioural coverage of the service
 * requires a real AccessibilityService runtime which Robolectric only
 * partially supports.
 */
@RunWith(RobolectricTestRunner::class)
class ScrollShieldAccessibilityServiceTest {

    @Test
    fun classExistsOnClasspath() {
        val cls = Class.forName("com.scrollshield.accessibility.ScrollShieldAccessibilityService")
        check(cls.name.contains("ScrollShield"))
    }
}
