package com.scrollshield.classification

import com.scrollshield.data.db.SignatureDao
import com.scrollshield.data.model.AdSignature
import com.scrollshield.data.model.Classification
import com.scrollshield.data.model.FeedItem
import com.scrollshield.data.model.TopicCategory
import com.scrollshield.util.PerceptualHash
import com.scrollshield.util.SimHash
import com.scrollshield.util.TextNormaliser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureMatcher @Inject constructor(
    private val signatureDao: SignatureDao
) {
    data class MatchResult(
        val classification: Classification,
        val confidence: Float,
        val topicVector: FloatArray,
        val topicCategory: TopicCategory
    )

    suspend fun match(feedItem: FeedItem): MatchResult? {
        val now = System.currentTimeMillis()
        val activeSignatures = signatureDao.getActive(now)
        if (activeSignatures.isEmpty()) return null

        // Text signature matching
        val normalised = TextNormaliser.normalise(feedItem.captionText)
        if (normalised.isNotBlank()) {
            val textHash = SimHash.hash(normalised)
            for (sig in activeSignatures) {
                if (SimHash.hammingDistance(textHash, sig.simHash) <= 3 && sig.confidence > 0.95f) {
                    return MatchResult(
                        classification = Classification.OFFICIAL_AD,
                        confidence = sig.confidence,
                        topicVector = FloatArray(20),
                        topicCategory = TopicCategory.fromIndex(0)
                    )
                }
            }
        }

        // Visual signature matching
        val bitmap = feedItem.screenCapture
        if (bitmap != null) {
            val visualHash = PerceptualHash.perceptualHash(bitmap)
            for (sig in activeSignatures) {
                val sigVisualHash = sig.visualHash ?: continue
                val sigHashLong = sigVisualHash.toLongOrNull() ?: continue
                if (SimHash.hammingDistance(visualHash, sigHashLong) <= 8 && sig.confidence > 0.95f) {
                    return MatchResult(
                        classification = Classification.OFFICIAL_AD,
                        confidence = sig.confidence,
                        topicVector = FloatArray(20),
                        topicCategory = TopicCategory.fromIndex(0)
                    )
                }
            }
        }

        return null
    }
}
