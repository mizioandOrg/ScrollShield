package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ad_signatures")
data class AdSignature(
    @PrimaryKey val id: String,
    val advertiser: String,
    val category: String,
    val captionHash: String,
    val simHash: Long,          // 64-bit SimHash for fast Tier 1 matching
    val confidence: Float,      // 0.0-1.0
    val firstSeen: Long,
    val expires: Long,
    val source: String,
    val locale: String?,
    val visualHash: String?     // Perceptual hash of ad screenshot for visual signature matching
)
