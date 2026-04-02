package com.scrollshield.util

import org.junit.Assert.*
import org.junit.Test

class SimHashTest {

    @Test
    fun `identical inputs produce identical hashes`() {
        val h1 = SimHash.hash("hello world")
        val h2 = SimHash.hash("hello world")
        assertEquals(h1, h2)
    }

    @Test
    fun `hammingDistance of identical hashes is zero`() {
        val h = SimHash.hash("test input")
        assertEquals(0, SimHash.hammingDistance(h, h))
    }

    @Test
    fun `hammingDistance counts differing bits via XOR popcount`() {
        // 0b0101L vs 0b0110L differ in 2 bits
        assertEquals(2, SimHash.hammingDistance(0b0101L, 0b0110L))
    }

    @Test
    fun `similar texts have smaller Hamming distance than dissimilar texts`() {
        val base = SimHash.hash("the quick brown fox")
        val similar = SimHash.hash("the quick brown fox jumps")
        val dissimilar = SimHash.hash("completely different words here now")
        val similarDist = SimHash.hammingDistance(base, similar)
        val dissimilarDist = SimHash.hammingDistance(base, dissimilar)
        assertTrue("similar dist $similarDist should be < dissimilar dist $dissimilarDist",
            similarDist < dissimilarDist)
    }

    @Test
    fun `empty string returns 0`() {
        assertEquals(0L, SimHash.hash(""))
    }

    @Test
    fun `blank string returns 0`() {
        assertEquals(0L, SimHash.hash("   "))
    }
}
