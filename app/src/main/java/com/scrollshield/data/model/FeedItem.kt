package com.scrollshield.data.model

import android.graphics.Bitmap
import android.graphics.Rect

data class FeedItem(
    val id: String,                     // SHA-256 of (captionText + creatorName + app + feedPosition)
    val timestamp: Long,                // Unix epoch ms
    val app: String,                    // Package name of source app
    val creatorName: String,
    val captionText: String,
    val hashtags: List<String>,
    val labelText: String?,             // "Sponsored" / "Ad" label if found
    val screenRegion: Rect,
    /**
     * Debug only — stripped in release builds (ProGuard/R8), max 4 KB.
     */
    val rawNodeDump: String,
    val feedPosition: Int,              // Position in the feed back-stack (0 = first loaded)
    val accessibilityNodeId: Long?,     // Accessibility node identifier for re-verification
    val detectedDurationMs: Long?,      // Duration of content if detectable from accessibility tree
    val screenCapture: Bitmap?,         // Screen capture from MediaProjection, null if unavailable
)
