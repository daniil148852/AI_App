package com.ai.assistant.data.local.dao

import androidx.room.*
import com.ai.assistant.data.local.entity.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CommandHistoryEntity>>

    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommandHistoryEntity): Long

    @Update
    suspend fun update(entity: CommandHistoryEntity)

    @Query("DELETE FROM command_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_history")
    suspend fun deleteAll()
}
