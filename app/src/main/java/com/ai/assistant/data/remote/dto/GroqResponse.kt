package com.ai.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GroqChatResponse(
    val id: String,
    @SerializedName("object")
    val objectType: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
) {
    data class Choice(
        val index: Int,
        val message: GroqMessage,
        @SerializedName("finish_reason")
        val finishReason: String?
    )

    data class Usage(
        @SerializedName("prompt_tokens")
        val promptTokens: Int,
        @SerializedName("completion_tokens")
        val completionTokens: Int,
        @SerializedName("total_tokens")
        val totalTokens: Int
    )
}
