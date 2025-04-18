package com.example.jetsonapp.internet

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface ApiStreamingService {
    @POST("api/generate")
    @Streaming
    suspend fun generate(
        @Body request: Any
    ): Response<ResponseBody>
}

data class GenerateImageRequest(
    val prompt: String,
)
