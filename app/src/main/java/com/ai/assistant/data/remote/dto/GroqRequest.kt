package com.ai.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.1,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096,
    @SerializedName("top_p")
    val topP: Double = 0.9,
    @SerializedName("response_format")
    val responseFormat: ResponseFormat? = ResponseFormat("json_object")
) {
    data class ResponseFormat(val type: String)
}

data class GroqMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)
