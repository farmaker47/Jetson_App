package com.example.jetsonapp.internet

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/generate")
    suspend fun generate(
        @Body request: GenerateRequest
    ): Response<GenerateResponse>
}

data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean
)

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
