package com.scrollshield.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scrollshield.data.db.ProfileDao
import com.scrollshield.data.db.ScrollShieldDatabase
import com.scrollshield.data.db.SessionDao
import com.scrollshield.data.db.SignatureDao
import com.scrollshield.error.ErrorRecoveryManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScrollShieldDatabase(
        @ApplicationContext context: Context,
        errorRecoveryManager: ErrorRecoveryManager
    ): ScrollShieldDatabase {
        val dbName = "scrollshield.db"
        val db = buildDatabase(context, dbName, errorRecoveryManager)
        return try {
            db.openHelper.writableDatabase
            db
        } catch (e: Exception) {
            if (isDatabaseCorruption(e)) {
                errorRecoveryManager.onDatabaseCorruption(dbName)
                try { db.close() } catch (_: Exception) {}
                context.deleteDatabase(dbName)
                errorRecoveryManager.onDatabaseRecreated(dbName)
                buildDatabase(context, dbName, errorRecoveryManager)
            } else {
                throw e
            }
        }
    }

    private fun buildDatabase(
        context: Context,
        dbName: String,
        errorRecoveryManager: ErrorRecoveryManager
    ): ScrollShieldDatabase {
        return Room.databaseBuilder(context, ScrollShieldDatabase::class.java, dbName)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    errorRecoveryManager.onDatabaseRecreated(dbName)
                }
            })
            .build()
    }

    private fun isDatabaseCorruption(e: Exception): Boolean {
        if (e is android.database.sqlite.SQLiteDatabaseCorruptException) return true
        if (e.cause is android.database.sqlite.SQLiteDatabaseCorruptException) return true
        val msg = e.message ?: ""
        return msg.contains("corrupt", ignoreCase = true) || msg.contains("malformed", ignoreCase = true)
    }

    @Provides
    fun provideProfileDao(database: ScrollShieldDatabase): ProfileDao =
        database.profileDao()

    @Provides
    fun provideSessionDao(database: ScrollShieldDatabase): SessionDao =
        database.sessionDao()

    @Provides
    fun provideSignatureDao(database: ScrollShieldDatabase): SignatureDao =
        database.signatureDao()
}
