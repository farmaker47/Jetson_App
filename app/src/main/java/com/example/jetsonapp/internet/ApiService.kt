package com.example.jetsonapp.internet

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

// Use for not streaming
interface ApiService {
    @POST("api/generate")
    suspend fun generate(
        @Body request: GenerateRequest
    ): Response<GenerateResponse>
}

interface ApiStreamingService {
    @POST("api/generate")
    @Streaming
    suspend fun generate(
        @Body request: GenerateRequest
    ): Response<ResponseBody>
}

data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val images: Array<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GenerateRequest

        if (model != other.model) return false
        if (prompt != other.prompt) return false
        if (stream != other.stream) return false
        if (!images.contentEquals(other.images)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = model.hashCode()
        result = 31 * result + prompt.hashCode()
        result = 31 * result + stream.hashCode()
        result = 31 * result + images.contentHashCode()
        return result
    }
}

data class GenerateResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean,
    val done_reason: String,
    val context: List<Long>,
    val total_duration: Long,
    val load_duration: Long,
    val prompt_eval_count: Int,
    val prompt_eval_duration: Long,
    val eval_count: Int,
    val eval_duration: Long
)

// Result from the server when stream = true is used.
// {"model":"gemma3:4b","created_at":"2025-04-02T05:57:31.316978184Z","response":"The","done":false}
data class GenerateStreamResponse(
    val model: String,
    val created_at: String,
    val response: String,
    val done: Boolean
)
