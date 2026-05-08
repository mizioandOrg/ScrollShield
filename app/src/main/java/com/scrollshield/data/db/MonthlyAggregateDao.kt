package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.MonthlyAggregate

@Dao
interface MonthlyAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: MonthlyAggregate)

    @Query("SELECT * FROM monthly_aggregates ORDER BY periodStartMs DESC")
    suspend fun getAll(): List<MonthlyAggregate>

    @Query("SELECT * FROM monthly_aggregates WHERE id = :id")
    suspend fun getById(id: String): MonthlyAggregate?

    @Query("SELECT * FROM monthly_aggregates WHERE periodStartMs >= :since ORDER BY periodStartMs DESC")
    suspend fun getSince(since: Long): List<MonthlyAggregate>
}
