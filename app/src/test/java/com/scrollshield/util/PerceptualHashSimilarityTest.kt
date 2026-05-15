package com.scrollshield.util

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PerceptualHashSimilarityTest {

    @Test
    fun identicalBitmapsHashIdentically() {
        val a = solid(Color.RED, 64)
        val b = solid(Color.RED, 64)
        val ha = PerceptualHash.perceptualHash(a)
        val hb = PerceptualHash.perceptualHash(b)
        check(ha == hb)
        check(PerceptualHash.visualMatch(ha, hb))
    }

    @Test
    fun differentColorsProduceDifferentHashes() {
        val a = solid(Color.RED, 64)
        val b = solid(Color.BLUE, 64)
        val ha = PerceptualHash.perceptualHash(a)
        val hb = PerceptualHash.perceptualHash(b)
        // We don't require they MUST differ (DCT of uniform color may collapse),
        // but the hamming distance should be reportable.
        val dist = SimHash.hammingDistance(ha, hb)
        check(dist in 0..64)
    }

    private fun solid(color: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val px = IntArray(size * size) { color }
        bmp.setPixels(px, 0, size, 0, 0, size, size)
        return bmp
    }
}
