package com.scrollshield.classification

import android.content.Context
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.error.ErrorRecoveryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val errorRecoveryManager: ErrorRecoveryManager
) {
    data class ContentResult(
        val classification: Classification,
        val confidence: Float,
        val topicVector: FloatArray,
        val topicCategory: TopicCategory
    )

    companion object {
        private const val MODEL_FILE = "scrollshield_text_classifier.tflite"
        private const val VOCAB_FILE = "wordpiece_vocab.txt"
        private const val MAX_TOKENS = 128
        private const val NUM_CLASSES = 7
        private const val NUM_TOPICS = 20
        private const val PAD_TOKEN_ID = 0
        private const val UNK_TOKEN_ID = 100
        private const val CLS_TOKEN_ID = 101
        private const val SEP_TOKEN_ID = 102
    }

    private val classificationMap = arrayOf(
        Classification.ORGANIC,
        Classification.OFFICIAL_AD,
        Classification.INFLUENCER_PROMO,
        Classification.ENGAGEMENT_BAIT,
        Classification.OUTRAGE_TRIGGER,
        Classification.EDUCATIONAL,
        Classification.UNKNOWN
    )

    private var interpreter: Interpreter? = null
    private var vocab: Map<String, Int>? = null

    @Synchronized
    private fun getInterpreter(): Interpreter? {
        interpreter?.let { return it }
        val startTime = System.currentTimeMillis()
        return try {
            val model = loadModelFile()
            val options = Interpreter.Options().apply { setNumThreads(2) }
            val interp = Interpreter(model, options)
            interpreter = interp
            val loadTimeMs = System.currentTimeMillis() - startTime
            errorRecoveryManager.onTextModelLoaded(loadTimeMs)
            interp
        } catch (e: Exception) {
            errorRecoveryManager.onTextModelLoadFailed(e.message ?: "unknown error")
            null
        }
    }

    @Synchronized
    private fun getVocab(): Map<String, Int> {
        vocab?.let { return it }
        val v = mutableMapOf<String, Int>()
        try {
            context.assets.open(VOCAB_FILE).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var idx = 0
                    reader.forEachLine { line ->
                        v[line.trim()] = idx++
                    }
                }
            }
        } catch (_: Exception) {}
        vocab = v
        return v
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    suspend fun classify(feedItem: FeedItem): ContentResult = withContext(Dispatchers.Default) {
        try {
            val interp = getInterpreter() ?: return@withContext ContentResult(
                classification = Classification.UNKNOWN,
                confidence = 0.0f,
                topicVector = FloatArray(NUM_TOPICS),
                topicCategory = TopicCategory.fromIndex(0)
            )
            val tokenIds = tokenize(feedItem.captionText)

            val inputIds = ByteBuffer.allocateDirect(MAX_TOKENS * 4).order(ByteOrder.nativeOrder())
            val attentionMask = ByteBuffer.allocateDirect(MAX_TOKENS * 4).order(ByteOrder.nativeOrder())
            for (id in tokenIds) {
                inputIds.putInt(id)
                attentionMask.putInt(if (id != PAD_TOKEN_ID) 1 else 0)
            }
            inputIds.rewind()
            attentionMask.rewind()

            val classOutput = Array(1) { FloatArray(NUM_CLASSES) }
            val topicOutput = Array(1) { FloatArray(NUM_TOPICS) }
            val outputs = mapOf(0 to classOutput, 1 to topicOutput)

            interp.runForMultipleInputsOutputs(arrayOf(inputIds, attentionMask), outputs)

            val classProbabilities = classOutput[0]
            val topicVector = topicOutput[0]

            var maxIdx = 0
            var maxConf = classProbabilities[0]
            for (i in 1 until NUM_CLASSES) {
                if (classProbabilities[i] > maxConf) {
                    maxConf = classProbabilities[i]
                    maxIdx = i
                }
            }

            var topicIdx = 0
            var topicMax = topicVector[0]
            for (i in 1 until NUM_TOPICS) {
                if (topicVector[i] > topicMax) {
                    topicMax = topicVector[i]
                    topicIdx = i
                }
            }

            ContentResult(
                classification = classificationMap[maxIdx],
                confidence = maxConf,
                topicVector = topicVector,
                topicCategory = TopicCategory.fromIndex(topicIdx)
            )
        } catch (_: Exception) {
            ContentResult(
                classification = Classification.UNKNOWN,
                confidence = 0.0f,
                topicVector = FloatArray(NUM_TOPICS),
                topicCategory = TopicCategory.fromIndex(0)
            )
        }
    }

    private fun tokenize(text: String): IntArray {
        val vocabMap = getVocab()
        val tokens = IntArray(MAX_TOKENS) { PAD_TOKEN_ID }
        tokens[0] = CLS_TOKEN_ID

        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        var pos = 1
        for (word in words) {
            if (pos >= MAX_TOKENS - 1) break
            val subTokens = wordPieceTokenize(word, vocabMap)
            for (subToken in subTokens) {
                if (pos >= MAX_TOKENS - 1) break
                tokens[pos++] = subToken
            }
        }
        if (pos < MAX_TOKENS) tokens[pos] = SEP_TOKEN_ID

        return tokens
    }

    private fun wordPieceTokenize(word: String, vocabMap: Map<String, Int>): List<Int> {
        val result = mutableListOf<Int>()
        var remaining = word
        var isFirst = true
        while (remaining.isNotEmpty()) {
            val prefix = if (isFirst) remaining else "##$remaining"
            val tokenId = vocabMap[prefix]
            if (tokenId != null) {
                result.add(tokenId)
                break
            }
            var found = false
            for (end in remaining.length - 1 downTo 1) {
                val sub = if (isFirst) remaining.substring(0, end) else "##${remaining.substring(0, end)}"
                val subId = vocabMap[sub]
                if (subId != null) {
                    result.add(subId)
                    remaining = remaining.substring(end)
                    isFirst = false
                    found = true
                    break
                }
            }
            if (!found) {
                result.add(UNK_TOKEN_ID)
                break
            }
        }
        return result
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
