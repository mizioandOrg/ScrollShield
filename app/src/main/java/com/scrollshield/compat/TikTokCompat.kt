package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

class TikTokCompat : AppCompatLayer() {
    override fun extractCreator(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/title")
            .firstOrNull()?.text?.toString()
    override fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/desc")
            .firstOrNull()?.text?.toString()
    override fun extractAdLabel(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId("com.zhiliaoapp.musically:id/ad_label")
            .firstOrNull()?.text?.toString()
    override fun extractHashtags(root: AccessibilityNodeInfo): List<String> {
        val caption = extractCaption(root) ?: return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }
}
