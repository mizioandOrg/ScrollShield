package com.scrollshield.testdata

import android.graphics.Rect
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.TopicCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Test-side JSONL loader for the synthetic dataset produced by
 * ml/dataset/generate_text_data.py.
 *
 * Provides the same FeedItem records the Python generator emits, ensuring
 * byte-identity by reading from the same JSONL bytes that ship in
 * src/test/resources/dataset/.
 */
object SyntheticFeedGenerator {

    data class Record(
        val feedItem: FeedItem,
        val expectedClassification: Classification,
        val expectedTopic: TopicCategory,
        val childUnsafe: Boolean,
    )

    /** Load and parse the bundled JSONL fixture from test resources. */
    fun load(resourcePath: String): List<Record> {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
            ?: error("Test fixture not on classpath: $resourcePath")
        val out = mutableListOf<Record>()
        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                val obj = JSONObject(trimmed)
                val hashtagsJson = obj.optJSONArray("hashtags") ?: JSONArray()
                val hashtags = (0 until hashtagsJson.length()).map { hashtagsJson.getString(it) }
                val feedItem = FeedItem(
                    id = obj.optString("id"),
                    timestamp = obj.optLong("seed", 0L),
                    app = obj.optString("app"),
                    creatorName = obj.optString("creator"),
                    captionText = obj.optString("caption"),
                    hashtags = hashtags,
                    labelText = if (obj.has("labelText")) obj.optString("labelText") else null,
                    screenRegion = Rect(),
                    rawNodeDump = "",
                    feedPosition = 0,
                    accessibilityNodeId = null,
                    detectedDurationMs = null,
                    screenCapture = null,
                )
                val expected = Classification.valueOf(
                    obj.optString("expectedClassification", "UNKNOWN")
                )
                val topicName = obj.optString("expectedTopic", "COMEDY")
                val topic = runCatching { TopicCategory.valueOf(topicName) }
                    .getOrDefault(TopicCategory.COMEDY)
                out += Record(
                    feedItem = feedItem,
                    expectedClassification = expected,
                    expectedTopic = topic,
                    childUnsafe = obj.optBoolean("childUnsafe", false),
                )
            }
        }
        return out
    }

    fun loadFeed200(): List<Record> = load("dataset/text_feed_200.jsonl")
    fun loadChildSafety50(): List<Record> = load("dataset/text_child_safety_50.jsonl")
}
