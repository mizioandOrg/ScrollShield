package com.scrollshield.util

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PerceptualHashTest {

    @Test
    fun `identical bitmaps produce identical hashes`() {
        val bmp = createGradientBitmap(32, 32)
        assertEquals(SimHash.hammingDistance(
            PerceptualHash.perceptualHash(bmp),
            PerceptualHash.perceptualHash(bmp)
        ), 0)
    }

    @Test
    fun `hashFromPixels is deterministic`() {
        val pixels = IntArray(32 * 32) { it * 255 / (32 * 32) }
        assertEquals(PerceptualHash.hashFromPixels(pixels), PerceptualHash.hashFromPixels(pixels))
    }

    @Test
    fun `visualMatch returns true within threshold`() {
        val h = 0b1010_1010L
        assertTrue(PerceptualHash.visualMatch(h, h))
    }

    @Test
    fun `visualMatch returns false beyond threshold`() {
        // Two longs that differ in all 64 bits
        assertFalse(PerceptualHash.visualMatch(0L, -1L, threshold = 8))
    }

    @Test
    fun `perceptualHash Hamming distance le 8 for same scene at different resolutions`() {
        // Same gradient scene rendered at two different sizes
        val bitmap64 = createGradientBitmap(64, 64)
        val bitmap32 = createGradientBitmap(32, 32)
        val hash64 = PerceptualHash.perceptualHash(bitmap64)
        val hash32 = PerceptualHash.perceptualHash(bitmap32)
        val dist = SimHash.hammingDistance(hash64, hash32)
        assertTrue("Expected Hamming distance <= 8 but got $dist", dist <= 8)
    }

    private fun createGradientBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bmp
    }
}
