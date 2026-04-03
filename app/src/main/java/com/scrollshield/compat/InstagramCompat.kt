package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class InstagramCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_title")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_caption")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/sponsored_label")
            .firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
