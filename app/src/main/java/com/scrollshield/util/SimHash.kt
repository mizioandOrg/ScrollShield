package com.scrollshield.util

object SimHash {

    private const val FNV_SEED = -3750763034362895579L   // 14695981039346656037 as signed Long
    private const val FNV_PRIME = 1099511628211L

    fun hash(text: String): Long {
        if (text.isBlank()) return 0L
        val tokens = text.trim().split(Regex("\\s+"))
        val weights = LongArray(64)
        for (token in tokens) {
            val h = fnv1a64(token)
            for (i in 0 until 64) {
                if ((h ushr i) and 1L == 1L) weights[i]++ else weights[i]--
            }
        }
        var result = 0L
        for (i in 0 until 64) {
            if (weights[i] > 0) result = result or (1L shl i)
        }
        return result
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun fnv1a64(s: String): Long {
        var h = FNV_SEED
        for (ch in s) {
            h = h xor ch.code.toLong()
            h *= FNV_PRIME
        }
        return h
    }
}
