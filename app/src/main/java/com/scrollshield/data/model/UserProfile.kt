package com.scrollshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalTime

@Entity
data class UserProfile(
    @PrimaryKey val id: String,
    val name: String,
    val isChildProfile: Boolean,
    val interestVector: FloatArray,
    val blockedCategories: Set<TopicCategory>,
    val blockedClassifications: Set<Classification>,
    val timeBudgets: Map<String, Int>,
    val maskEnabled: Boolean,
    val counterEnabled: Boolean,
    val maskDismissable: Boolean,
    val pinProtected: Boolean,
    val parentPinHash: String?,
    val satisfactionHistory: List<Float>,
    val scoringWeights: ScoringWeights,
    val createdAt: Long,
    val updatedAt: Long,
    val autoActivateSchedule: Pair<LocalTime, LocalTime>?
)

data class ScoringWeights(
    val interest: Float = 0.35f,
    val wellbeing: Float = 0.25f,
    val novelty: Float = 0.15f,
    val manipulation: Float = 0.25f
)

class TopicCategorySetConverter {
    @TypeConverter
    fun fromSet(categories: Set<TopicCategory>): String =
        categories.joinToString(",") { it.name }

    @TypeConverter
    fun toSet(value: String): Set<TopicCategory> =
        if (value.isBlank()) emptySet()
        else value.split(",").map { TopicCategory.valueOf(it) }.toSet()
}

class ClassificationSetConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromSet(values: Set<Classification>): String =
        gson.toJson(values.map { it.name })

    @TypeConverter
    fun toSet(value: String): Set<Classification> {
        val type = object : TypeToken<List<String>>() {}.type
        val list: List<String> = gson.fromJson(value, type)
        return list.map { Classification.valueOf(it) }.toSet()
    }
}

class StringIntMapConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<String, Int>): String = gson.toJson(map)

    @TypeConverter
    fun toMap(value: String): Map<String, Int> {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(value, type)
    }
}

class FloatListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<Float>): String = gson.toJson(list)

    @TypeConverter
    fun toList(value: String): List<Float> {
        val type = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, type)
    }
}

class ScoringWeightsConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromScoringWeights(weights: ScoringWeights): String = gson.toJson(weights)

    @TypeConverter
    fun toScoringWeights(value: String): ScoringWeights =
        gson.fromJson(value, ScoringWeights::class.java)
}

class LocalTimeScheduleConverter {
    @TypeConverter
    fun fromSchedule(schedule: Pair<LocalTime, LocalTime>?): String? =
        schedule?.let { "${it.first}|${it.second}" }

    @TypeConverter
    fun toSchedule(value: String?): Pair<LocalTime, LocalTime>? {
        if (value == null) return null
        val parts = value.split("|")
        return Pair(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]))
    }
}
