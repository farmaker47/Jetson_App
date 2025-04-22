package com.example.jetsonapp.internet

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming


data class GenerateImageRequest(
    val prompt: String,
    val stream: Boolean,
    val images: List<String>
)

data class GenerateRequest(
    val prompt: String,
    val stream: Boolean
)

///////////////////////////////////////////////
// Kokoro section
interface KokoroService {
    @POST("api/generate")
    @Streaming
    suspend fun generate(
        @Body request: Any
    ): Response<ResponseBody>
}

data class KokoroRequest(
    val prompt: String,
    val voice: String,
)
