package com.example.jetsonapp.internet

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

// Use for not streaming
//interface ApiService {
//    @POST("api/generate")
//    suspend fun generate(
//        @Body request: Any
//    ): Response<GenerateResponse>
//}

/////////////////////////////////////////////////////
// For HF transformers and SmolVLM2
// curl -X POST https://8fdd-2a02-2149-8a02-e000-ecc3-e96c-f10e-b812.ngrok-free.app/api/generate -H "Content-Type: application/json" -d '{
// "prompt": "Can you describe this image?",
// "stream": true,
// "images": ["iVBORw0KGgoAAAANSUhEUgAAAG0Tt4kVF0kLUYYmCCtD/DrQ5YhMGbA9L3ucdjh0y8kOHW5gU/VEEmJTcL4Pz/f7mgoAbYkAAAAAElFTkSuQmCC"]}'

// Apparently this interface works with streaming and not streaming
// Change the appropriate value stream = true in the view model.
interface ApiStreamingService {
    @POST("api/generate")
    @Streaming
    suspend fun generate(
        @Body request: Any
    ): Response<ResponseBody>
}

data class GenerateImageRequest(
    val prompt: String,
    val stream: Boolean,
    val images: List<String>
)

// Result from the server.
// {"result":"some result"}
data class GenerateStreamResponse(
    val result: String,
)
