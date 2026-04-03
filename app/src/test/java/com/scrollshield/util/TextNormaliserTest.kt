package com.scrollshield.util

import org.junit.Assert.*
import org.junit.Test

class TextNormaliserTest {

    @Test
    fun `lowercases input`() {
        assertEquals("hello world", TextNormaliser.normalise("Hello World"))
    }

    @Test
    fun `removes URLs`() {
        val result = TextNormaliser.normalise("Visit https://example.com for more")
        assertFalse(result.contains("https://"))
        assertTrue(result.contains("visit"))
        assertTrue(result.contains("for more"))
    }

    @Test
    fun `removes at-mentions`() {
        val result = TextNormaliser.normalise("Hello @user how are you")
        assertFalse(result.contains("@user"))
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("how are you"))
    }

    @Test
    fun `collapses whitespace`() {
        assertEquals("a b c", TextNormaliser.normalise("a   b   c"))
    }

    @Test
    fun `no double spaces after URL removal`() {
        val result = TextNormaliser.normalise("before https://example.com after")
        assertFalse("Result should not contain double space: '$result'", result.contains("  "))
    }

    @Test
    fun `no double spaces after mention removal`() {
        val result = TextNormaliser.normalise("before @user after")
        assertFalse("Result should not contain double space: '$result'", result.contains("  "))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("hello", TextNormaliser.normalise("  hello  "))
    }

    @Test
    fun `rules applied in order — whitespace collapsed before URL removal`() {
        // After lowercase and emoji strip and whitespace collapse, URL is intact for removal
        val result = TextNormaliser.normalise("check https://foo.com out")
        assertFalse(result.contains("https://"))
        assertFalse(result.contains("  "))
    }
}
