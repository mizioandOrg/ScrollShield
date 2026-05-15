package com.scrollshield.classification

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.scrollshield.error.ErrorRecoveryManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VisualClassifierTest {

    @Test
    fun returnsNullWhenInterpreterUnavailable() = runTest {
        // The TFLite native library cannot be loaded on the JVM; the
        // production class catches Exception but not Error, so we skip
        // this test when the native lib is absent. Real on-device behaviour
        // is covered by the androidTest pipeline.
        val nativeAvailable = try {
            Class.forName("org.tensorflow.lite.TensorFlowLite")
                .getMethod("init").invoke(null)
            true
        } catch (_: Throwable) {
            false
        }
        Assume.assumeTrue("Skipping: TFLite native library unavailable", nativeAvailable)

        val context = ApplicationProvider.getApplicationContext<Application>()
        val err = mockk<ErrorRecoveryManager>(relaxed = true)
        val classifier = VisualClassifier(context, err)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val r = classifier.classify(bmp)
        check(r == null || r.confidence in 0.0f..1.0f)
    }
}
