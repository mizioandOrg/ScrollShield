package com.scrollshield.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.scrollshield.data.model.Classification

/**
 * Converts List<String> to/from JSON string.
 * Used for SessionRecord.adBrands, SessionRecord.adCategories.
 */
class StringListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}

/**
 * Converts Map<Classification, Int> to/from JSON string.
 * Used for SessionRecord.classificationCounts.
 */
class ClassificationIntMapConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromMap(map: Map<Classification, Int>): String {
        val stringKeyMap = map.entries.associate { (k, v) -> k.name to v }
        return gson.toJson(stringKeyMap)
    }

    @TypeConverter
    fun toMap(value: String): Map<Classification, Int> {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        val stringKeyMap: Map<String, Int> = gson.fromJson(value, type)
        return stringKeyMap.entries.associate { (k, v) -> Classification.valueOf(k) to v }
    }
}

/**
 * Converts FloatArray to/from JSON string.
 * Used for UserProfile.interestVector.
 */
class FloatArrayConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String = gson.toJson(array.toList())

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        val type = object : TypeToken<List<Float>>() {}.type
        val list: List<Float> = gson.fromJson(value, type)
        return list.toFloatArray()
    }
}
