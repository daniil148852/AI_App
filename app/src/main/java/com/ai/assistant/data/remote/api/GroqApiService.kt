package com.ai.assistant.data.remote.api

import com.ai.assistant.data.remote.dto.GroqChatRequest
import com.ai.assistant.data.remote.dto.GroqChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface GroqApiService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: GroqChatRequest
    ): GroqChatResponse
}
