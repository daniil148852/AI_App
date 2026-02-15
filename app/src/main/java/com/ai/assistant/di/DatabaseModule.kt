package com.ai.assistant.di

import android.content.Context
import androidx.room.Room
import com.ai.assistant.data.local.AppDatabase
import com.ai.assistant.data.local.dao.CommandHistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_assistant_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideCommandHistoryDao(db: AppDatabase): CommandHistoryDao {
        return db.commandHistoryDao()
    }
}
