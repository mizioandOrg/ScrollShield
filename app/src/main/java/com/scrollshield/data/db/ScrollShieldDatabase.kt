package com.scrollshield.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scrollshield.data.model.AdSignature
import com.scrollshield.data.model.ClassificationSetConverter
import com.scrollshield.data.model.FloatListConverter
import com.scrollshield.data.model.LocalTimeScheduleConverter
import com.scrollshield.data.model.ScoringWeightsConverter
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.data.model.StringIntMapConverter
import com.scrollshield.data.model.TopicCategorySetConverter
import com.scrollshield.data.model.UserProfile

// NOTE: An index on (profileId, startTime) for performance would need to be
// declared inside @Entity on SessionRecord. That file cannot be modified in this WI.
// Add `indices = [Index(value = ["profileId", "startTime"])]` to SessionRecord in WI-04.

@Database(
    entities = [SessionRecord::class, AdSignature::class, UserProfile::class],
    version = 1,
    autoMigrations = []
)
@TypeConverters(
    TopicCategorySetConverter::class,
    ClassificationSetConverter::class,
    StringListConverter::class,
    StringIntMapConverter::class,
    ClassificationIntMapConverter::class,
    FloatListConverter::class,
    FloatArrayConverter::class,
    ScoringWeightsConverter::class,
    LocalTimeScheduleConverter::class
)
abstract class ScrollShieldDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun signatureDao(): SignatureDao
    abstract fun profileDao(): ProfileDao
}
