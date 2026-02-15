package com.ai.assistant.di

import com.ai.assistant.data.repository.AiRepositoryImpl
import com.ai.assistant.data.repository.CommandHistoryRepositoryImpl
import com.ai.assistant.data.repository.SettingsRepositoryImpl
import com.ai.assistant.domain.repository.AiRepository
import com.ai.assistant.domain.repository.CommandHistoryRepository
import com.ai.assistant.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindCommandHistoryRepository(
        impl: CommandHistoryRepositoryImpl
    ): CommandHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
