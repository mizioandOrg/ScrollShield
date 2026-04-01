package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.SessionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionRecord)

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun getSessionsByProfile(profileId: String): Flow<List<SessionRecord>>

    @Query("SELECT * FROM sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: Long): List<SessionRecord>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsByProfileSince(profileId: String, since: Long): List<SessionRecord>

    @Query("DELETE FROM sessions WHERE startTime < :before")
    suspend fun deleteOlderThan(before: Long)
}
