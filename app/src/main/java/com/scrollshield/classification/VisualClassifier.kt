package com.scrollshield.classification

import android.content.Context
import android.graphics.Bitmap
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.TopicCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisualClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class VisualResult(
        val classification: Classification,
        val confidence: Float,
        val topicVector: FloatArray,
        val topicCategory: TopicCategory
    )

    companion object {
        private const val MODEL_FILE = "scrollshield_visual_classifier.tflite"
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 7
        private const val NUM_TOPICS = 20
        private const val STATUS_BAR_DP = 24
        private const val NAV_BAR_DP = 48
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

    @Synchronized
    private fun getInterpreter(): Interpreter {
        interpreter?.let { return it }
        val model = loadModelFile()
        val options = Interpreter.Options()
        try {
            val nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
        } catch (_: Exception) {
            options.setUseXNNPACK(true)
        }
        options.setNumThreads(2)
        val interp = Interpreter(model, options)
        interpreter = interp
        return interp
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    suspend fun classify(bitmap: Bitmap): VisualResult? = withContext(Dispatchers.Default) {
        try {
            val interp = getInterpreter()
            val preprocessed = preprocess(bitmap)
            val inputBuffer = bitmapToByteBuffer(preprocessed)
            if (preprocessed != bitmap) preprocessed.recycle()

            val classOutput = Array(1) { FloatArray(NUM_CLASSES) }
            val topicOutput = Array(1) { FloatArray(NUM_TOPICS) }
            val outputs = mapOf(0 to classOutput, 1 to topicOutput)

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

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

            VisualResult(
                classification = classificationMap[maxIdx],
                confidence = maxConf,
                topicVector = topicVector,
                topicCategory = TopicCategory.fromIndex(topicIdx)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun preprocess(bitmap: Bitmap): Bitmap {
        val density = context.resources.displayMetrics.density
        val statusBarPx = (STATUS_BAR_DP * density).toInt()
        val navBarPx = (NAV_BAR_DP * density).toInt()

        val cropTop = statusBarPx.coerceAtMost(bitmap.height)
        val cropBottom = navBarPx.coerceAtMost(bitmap.height - cropTop)
        val croppedHeight = (bitmap.height - cropTop - cropBottom).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, croppedHeight)
        val resized = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        if (cropped != bitmap) cropped.recycle()
        return resized
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte())
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte())
            byteBuffer.put((pixel and 0xFF).toByte())
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
