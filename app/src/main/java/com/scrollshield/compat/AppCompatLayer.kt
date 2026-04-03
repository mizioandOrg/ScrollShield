package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo

abstract class AppCompatLayer {
    abstract fun extractCreator(root: AccessibilityNodeInfo): String?
    abstract fun extractCaption(root: AccessibilityNodeInfo): String?
    abstract fun extractAdLabel(root: AccessibilityNodeInfo): String?
    abstract fun extractHashtags(root: AccessibilityNodeInfo): List<String>
}
