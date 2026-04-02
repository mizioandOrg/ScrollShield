package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.AdSignature

@Dao
interface SignatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(signatures: List<AdSignature>)

    @Query("SELECT * FROM ad_signatures WHERE expires > :now")
    suspend fun getActive(now: Long): List<AdSignature>

    @Query("DELETE FROM ad_signatures WHERE expires < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT COUNT(*) FROM ad_signatures")
    suspend fun count(): Int

    @Query("DELETE FROM ad_signatures WHERE source = 'synced' AND id IN (:ids)")
    suspend fun deleteSyncedByIds(ids: List<String>)

    @Query("SELECT visualHash FROM ad_signatures WHERE visualHash IS NOT NULL AND expires > :now")
    suspend fun getActiveVisualHashes(now: Long): List<String>
}
