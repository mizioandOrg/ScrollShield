package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class YouTubeCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_channel_name")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_multi_format_title")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? = null
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
