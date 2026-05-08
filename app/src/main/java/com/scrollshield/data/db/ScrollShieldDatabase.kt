package com.scrollshield.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scrollshield.data.model.AdSignature
import com.scrollshield.data.model.ClassificationSetConverter
import com.scrollshield.data.model.FloatListConverter
import com.scrollshield.data.model.LocalTimeScheduleConverter
import com.scrollshield.data.model.MonthlyAggregate
import com.scrollshield.data.model.ScoringWeightsConverter
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.data.model.StringIntMapConverter
import com.scrollshield.data.model.TopicCategorySetConverter
import com.scrollshield.data.model.UserProfile

@Database(
    entities = [SessionRecord::class, AdSignature::class, UserProfile::class, MonthlyAggregate::class],
    version = 2,
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
    abstract fun monthlyAggregateDao(): MonthlyAggregateDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_sessions_profile_starttime` " +
                        "ON `sessions` (`profileId`, `startTime`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `monthly_aggregates` (" +
                        "`id` TEXT NOT NULL, " +
                        "`yearMonth` TEXT NOT NULL, " +
                        "`periodStartMs` INTEGER NOT NULL, " +
                        "`periodEndMs` INTEGER NOT NULL, " +
                        "`totalSessions` INTEGER NOT NULL, " +
                        "`totalDurationMinutes` REAL NOT NULL, " +
                        "`totalAdsDetected` INTEGER NOT NULL, " +
                        "`totalAdsSkipped` INTEGER NOT NULL, " +
                        "`averageSatisfaction` REAL, " +
                        "`perAppBreakdownJson` TEXT NOT NULL, " +
                        "`topTenBrandsJson` TEXT NOT NULL, " +
                        "`tierDistributionJson` TEXT NOT NULL, " +
                        "`visualClassifierFeedbackJson` TEXT NOT NULL, " +
                        "`computedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
