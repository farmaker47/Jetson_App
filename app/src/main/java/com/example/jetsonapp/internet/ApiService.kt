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
    val result: String
)
