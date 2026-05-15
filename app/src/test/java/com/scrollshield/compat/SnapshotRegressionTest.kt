package com.scrollshield.compat

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads the bundled accessibility snapshots, builds FakeAccessibilityNode
 * trees via MockK, and asserts each compat layer extracts the expected
 * caption/creator/labelText.
 */
@RunWith(RobolectricTestRunner::class)
class SnapshotRegressionTest {

    @Test
    fun instagramExtractsExpectedFields() {
        runApp("snapshots/instagram_v300.json", InstagramCompat())
    }

    @Test
    fun tiktokExtractsExpectedFields() {
        runApp("snapshots/tiktok_v32.json", TikTokCompat())
    }

    @Test
    fun youtubeExtractsExpectedFields() {
        runApp("snapshots/youtube_v18.json", YouTubeCompat())
    }

    private fun runApp(resourcePath: String, compat: AppCompatLayer) {
        val text = readResource(resourcePath)
        val root = JSONObject(text)
        val snapshots = root.getJSONArray("snapshots")
        for (i in 0 until snapshots.length()) {
            val snap = snapshots.getJSONObject(i)
            val byId = mutableMapOf<String, MutableList<AccessibilityNodeInfo>>()
            val nodes = snap.getJSONArray("nodes")
            for (j in 0 until nodes.length()) {
                val n = nodes.getJSONObject(j)
                val vid = n.getString("viewIdResourceName")
                val txt = n.getString("text")
                val node = mockk<AccessibilityNodeInfo>(relaxed = true)
                every { node.text } returns txt
                byId.getOrPut(vid) { mutableListOf() }.add(node)
            }
            val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
            every { rootNode.findAccessibilityNodeInfosByViewId(any()) } answers {
                byId[firstArg<String>()] ?: emptyList()
            }
            val expectedCreator = snap.optString("expectedCreator", "")
            val expectedCaption = snap.optString("expectedCaption", "")
            val expectedLabel = if (snap.isNull("expectedLabel")) null else snap.optString("expectedLabel")

            val gotCreator = compat.extractCreator(rootNode)
            val gotCaption = compat.extractCaption(rootNode)
            val gotLabel = compat.extractAdLabel(rootNode)

            check(gotCreator == expectedCreator) {
                "$resourcePath snap $i creator: expected=$expectedCreator got=$gotCreator"
            }
            check(gotCaption == expectedCaption) {
                "$resourcePath snap $i caption: expected=$expectedCaption got=$gotCaption"
            }
            check(gotLabel == expectedLabel) {
                "$resourcePath snap $i label: expected=$expectedLabel got=$gotLabel"
            }
        }
    }

    private fun readResource(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Missing resource: $path")
        BufferedReader(InputStreamReader(stream)).use { return it.readText() }
    }
}
