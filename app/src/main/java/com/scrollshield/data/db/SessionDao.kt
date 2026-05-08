package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.scrollshield.data.model.SessionRecord
import com.scrollshield.reports.AggregateRow
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

    @Query("SELECT COUNT(*) FROM sessions WHERE startTime >= :since AND startTime < :until")
    suspend fun countInRange(since: Long, until: Long): Int

    @Query("SELECT * FROM sessions WHERE startTime >= :since AND startTime < :until ORDER BY startTime ASC")
    suspend fun getSessionsInRange(since: Long, until: Long): List<SessionRecord>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId AND startTime >= :since AND startTime < :until ORDER BY startTime ASC")
    suspend fun getSessionsByProfileInRange(profileId: String, since: Long, until: Long): List<SessionRecord>

    @RawQuery
    suspend fun rawAggregate(query: SupportSQLiteQuery): List<AggregateRow>
}
