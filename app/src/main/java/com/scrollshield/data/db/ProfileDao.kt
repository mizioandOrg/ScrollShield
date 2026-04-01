package com.scrollshield.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scrollshield.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM UserProfile WHERE id = :id")
    suspend fun getById(id: String): UserProfile?

    @Query("SELECT * FROM UserProfile")
    fun getAllProfiles(): Flow<List<UserProfile>>

    @Delete
    suspend fun delete(profile: UserProfile)
}
