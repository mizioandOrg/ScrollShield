package com.scrollshield.data.model

/**
 * In-memory map of classified feed items for the current scroll session.
 *
 * Lifecycle rules:
 * - **Target-to-target**: Discard ScanMap, finalize session, start new pre-scan.
 * - **Return within 60s**: Retain ScanMap, validate via [lastValidatedHash].
 *   Match = reuse existing map; mismatch = re-scan.
 * - **Return after 60s**: Discard ScanMap and perform a fresh pre-scan.
 */
data class ScanMap(
    val sessionId: String,
    val app: String,
    val items: MutableList<ClassifiedItem>,
    val scanHead: Int,
    val userHead: Int,
    val skipIndices: Set<Int>,
    val isExtending: Boolean = false,
    val lastValidatedHash: String? = null
)
