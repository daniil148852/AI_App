package com.ai.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ai.assistant.data.local.dao.CommandHistoryDao
import com.ai.assistant.data.local.entity.CommandHistoryEntity

@Database(
    entities = [CommandHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
}
