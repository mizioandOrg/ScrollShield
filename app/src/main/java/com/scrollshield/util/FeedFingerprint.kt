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
