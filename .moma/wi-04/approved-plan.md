# Approved Plan — Iteration 6 (Score: 10/10)

## Changes to /home/devuser/dev-worktree-1/app/src/main/java/com/scrollshield/util/FeedFingerprint.kt

Complete implementation to write:

```kotlin
package com.scrollshield.util

import java.security.MessageDigest

object FeedFingerprint {

    data class NodeData(
        val className: String,
        val viewIdResourceName: String,
        val text: String,
        val contentDescription: String
    )

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("|", "\\|")

    fun fingerprint(nodes: List<NodeData>): String {
        val sorted = nodes.sortedWith(
            compareBy({ it.viewIdResourceName }, { it.className })
        )
        val concatenated = sorted.joinToString(separator = "\n") { n ->
            "${escape(n.className)}|${escape(n.viewIdResourceName)}" +
            "|${escape(n.text.take(64))}|${escape(n.contentDescription.take(64))}"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(concatenated.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

## Changes to /home/devuser/dev-worktree-1/app/src/test/java/com/scrollshield/util/FeedFingerprintTest.kt

Complete test implementation to write:

```kotlin
package com.scrollshield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class FeedFingerprintTest {

    private fun sha256hex(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `empty list returns SHA-256 of empty string`() {
        val result = FeedFingerprint.fingerprint(emptyList())
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result
        )
    }

    @Test
    fun `single node produces correct SHA-256`() {
        val node = FeedFingerprint.NodeData(
            className = "android.widget.TextView",
            viewIdResourceName = "com.example:id/title",
            text = "Hello World",
            contentDescription = ""
        )
        val expected = sha256hex(
            "android.widget.TextView|com.example:id/title|Hello World|"
        )
        assertEquals(expected, FeedFingerprint.fingerprint(listOf(node)))
    }

    @Test
    fun `deterministic — same nodes same order same hash`() {
        val nodes = listOf(
            FeedFingerprint.NodeData("android.widget.Button", "com.example:id/btn", "Click", ""),
            FeedFingerprint.NodeData("android.widget.ImageView", "com.example:id/img", "", "Profile photo")
        )
        assertEquals(
            FeedFingerprint.fingerprint(nodes),
            FeedFingerprint.fingerprint(nodes)
        )
    }

    @Test
    fun `different node sets produce different hashes`() {
        val a = listOf(
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/a", "Foo", "")
        )
        val b = listOf(
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/a", "Bar", "")
        )
        assertNotEquals(FeedFingerprint.fingerprint(a), FeedFingerprint.fingerprint(b))
    }

    @Test
    fun `nodes are sorted by viewIdResourceName then className`() {
        val nodes = listOf(
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/z", "Last", ""),
            FeedFingerprint.NodeData("android.widget.Button",   "com.example:id/a", "First", "")
        )
        val nodesReversed = listOf(
            FeedFingerprint.NodeData("android.widget.Button",   "com.example:id/a", "First", ""),
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/z", "Last", "")
        )
        assertEquals(
            FeedFingerprint.fingerprint(nodes),
            FeedFingerprint.fingerprint(nodesReversed)
        )
    }

    @Test
    fun `secondary sort by className when viewIdResourceName equal`() {
        val nodes = listOf(
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/x", "T", ""),
            FeedFingerprint.NodeData("android.widget.Button",   "com.example:id/x", "B", "")
        )
        val nodesReversed = listOf(
            FeedFingerprint.NodeData("android.widget.Button",   "com.example:id/x", "B", ""),
            FeedFingerprint.NodeData("android.widget.TextView", "com.example:id/x", "T", "")
        )
        assertEquals(
            FeedFingerprint.fingerprint(nodes),
            FeedFingerprint.fingerprint(nodesReversed)
        )
    }

    @Test
    fun `text truncated to 64 chars`() {
        val longText = "A".repeat(100)
        val truncated = "A".repeat(64)
        val nodeA = FeedFingerprint.NodeData("android.widget.TextView", "id/x", longText, "")
        val nodeB = FeedFingerprint.NodeData("android.widget.TextView", "id/x", truncated, "")
        assertEquals(FeedFingerprint.fingerprint(listOf(nodeA)), FeedFingerprint.fingerprint(listOf(nodeB)))
    }

    @Test
    fun `contentDescription truncated to 64 chars`() {
        val longDesc = "D".repeat(100)
        val truncated = "D".repeat(64)
        val nodeA = FeedFingerprint.NodeData("android.widget.ImageView", "id/img", "", longDesc)
        val nodeB = FeedFingerprint.NodeData("android.widget.ImageView", "id/img", "", truncated)
        assertEquals(FeedFingerprint.fingerprint(listOf(nodeA)), FeedFingerprint.fingerprint(listOf(nodeB)))
    }

    @Test
    fun `pipe characters in fields are escaped`() {
        val node = FeedFingerprint.NodeData(
            className = "android.widget.TextView",
            viewIdResourceName = "id/x",
            text = "foo|bar",
            contentDescription = ""
        )
        val expected = sha256hex("android.widget.TextView|id/x|foo\\|bar|")
        assertEquals(expected, FeedFingerprint.fingerprint(listOf(node)))
    }
}
```

## Changes to Other Files

All other files (SimHash.kt, TextNormaliser.kt, CosineSimilarity.kt, PerceptualHash.kt and their tests) are written as established in prior iterations and documented in the Implementer prompt. No changes to those files.
