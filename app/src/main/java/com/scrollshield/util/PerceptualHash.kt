package com.scrollshield.util

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sqrt

object PerceptualHash {

    private const val DCT_SIZE = 32
    private const val HASH_SIZE = 8

    fun perceptualHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, DCT_SIZE, DCT_SIZE, true)
        val pixels = IntArray(DCT_SIZE * DCT_SIZE)
        scaled.getPixels(pixels, 0, DCT_SIZE, 0, 0, DCT_SIZE, DCT_SIZE)
        if (scaled != bitmap) scaled.recycle()
        return hashFromPixels(pixels)
    }

    fun hashFromPixels(pixels: IntArray): Long {
        val dct = computeDct2D(pixels)
        val coeffs = topLeftCoefficients(dct)
        val med = median(coeffs)
        var hash = 0L
        for (i in coeffs.indices) {
            if (coeffs[i] > med) hash = hash or (1L shl i)
        }
        return hash
    }

    fun visualMatch(a: Long, b: Long, threshold: Int = 8): Boolean =
        SimHash.hammingDistance(a, b) <= threshold

    private fun computeDct2D(pixels: IntArray): Array<DoubleArray> {
        // Convert to grayscale luminance
        val gray = Array(DCT_SIZE) { row ->
            DoubleArray(DCT_SIZE) { col ->
                val px = pixels[row * DCT_SIZE + col]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                0.299 * r + 0.587 * g + 0.114 * b
            }
        }
        // Row DCT
        val rowDct = Array(DCT_SIZE) { row ->
            DoubleArray(DCT_SIZE) { k ->
                var sum = 0.0
                for (n in 0 until DCT_SIZE) {
                    sum += gray[row][n] * cos(Math.PI * k * (2 * n + 1) / (2.0 * DCT_SIZE))
                }
                val scale = if (k == 0) sqrt(1.0 / DCT_SIZE) else sqrt(2.0 / DCT_SIZE)
                sum * scale
            }
        }
        // Column DCT
        return Array(DCT_SIZE) { row ->
            DoubleArray(DCT_SIZE) { col ->
                var sum = 0.0
                for (n in 0 until DCT_SIZE) {
                    sum += rowDct[n][col] * cos(Math.PI * row * (2 * n + 1) / (2.0 * DCT_SIZE))
                }
                val scale = if (row == 0) sqrt(1.0 / DCT_SIZE) else sqrt(2.0 / DCT_SIZE)
                sum * scale
            }
        }
    }

    private fun topLeftCoefficients(dct: Array<DoubleArray>): DoubleArray {
        val result = DoubleArray(HASH_SIZE * HASH_SIZE)
        var idx = 0
        for (row in 0 until HASH_SIZE) {
            for (col in 0 until HASH_SIZE) {
                result[idx++] = dct[row][col]
            }
        }
        return result
    }

    private fun median(arr: DoubleArray): Double {
        val sorted = arr.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
        else sorted[mid]
    }
}
