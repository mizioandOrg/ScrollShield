package com.scrollshield.util

import org.junit.Assert.*
import org.junit.Test

class CosineSimilarityTest {

    @Test
    fun `identical vectors return 1`() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, CosineSimilarity.cosineSimilarity(v, v), 1e-6f)
    }

    @Test
    fun `orthogonal vectors return 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        assertEquals(0f, CosineSimilarity.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `opposite vectors return -1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1f, CosineSimilarity.cosineSimilarity(a, b), 1e-6f)
    }

    @Test
    fun `zero vector returns 0`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val v    = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, CosineSimilarity.cosineSimilarity(zero, v), 1e-6f)
    }

    @Test
    fun `empty arrays return 0`() {
        assertEquals(0f, CosineSimilarity.cosineSimilarity(floatArrayOf(), floatArrayOf()), 1e-6f)
    }

    @Test
    fun `partial similarity is between 0 and 1`() {
        val a = floatArrayOf(1f, 1f, 0f)
        val b = floatArrayOf(1f, 0f, 0f)
        val sim = CosineSimilarity.cosineSimilarity(a, b)
        assertTrue(sim > 0f && sim < 1f)
    }
}
