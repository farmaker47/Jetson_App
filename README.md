## Overview
Running Gemma 3 with Ollama on Jetson Orin Nano and use the API response on android.

<img src="https://github.com/farmaker47/Jetson_App/blob/master/gemma.png" alt="Gemma 3 Logo" width="700">

---
## Some insights first about the project

### Key Components

- **Jetson Orin Nano**: Compact AI dev board with GPU acceleration; supports JetPack 6.1+ for improved performance.
- **Ollama**: Simplifies LLM deployment, supports many open-source models, optimized for local use.
- **Gemma 3**: Multilingual, 128K context window model based on Gemini 2.0 tech; supports text and image inputs.

## Pinging the Ollama Server from the Terminal

You can easily check the status of your Ollama server and send requests using terminal commands like `nc` and `curl`.

1.  **Check if the Ollama port is listening:**
    Use the `nc` (netcat) command to verify that the server is accepting connections on its default port (11434). Replace `192.168.1.92` with your Ollama server's IP address if it's different.

    ```bash
    nc -vz 192.168.1.92:11434
    ```
    A successful connection will typically output something like `Connection to 192.168.1.92 port 11434 [tcp/*] succeeded!`.

2.  **Send a basic generation request:**
    Use the `curl` command to send a POST request to the `/api/generate` endpoint. This example asks the `gemma3:1b` model a question.

    ```bash
    curl http://192.168.1.92:11434/api/generate -d '{
      "model": "gemma3:1b",
      "prompt":"Why my cat is not eating?",
      "stream": false
    }'
    ```
    Setting `"stream": false` means you'll get the entire response at once after the model finishes processing.

## Doing More with the Ollama API

Want to explore further? The Ollama API documentation provides examples for more advanced interactions, such as:

*   **Getting Structured Outputs:** Requesting responses in specific formats (like JSON).
*   **Sending Requests with Images:** Using multimodal models (like `gemma3:4b` in this example) to analyze images. The image data is sent as a base64 encoded string.

    ```bash
    curl http://192.168.1.92:11434/api/generate -d '{
      "model": "gemma3:4b",
      "prompt":"What is in this picture?",
      "stream": false,
      "images": ["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"]
    }'
    ```

Check the official [Ollama API documentation](https://github.com/ollama/ollama/blob/main/docs/api.md) for a complete list of endpoints, parameters, and more examples.

---

## For the android project

## Key Code Components for Ollama API Interaction (Android/Kotlin)

The most important parts of the code for interacting with the Ollama API from an Android application using Kotlin are:

1.  **The Retrofit Interface with Streaming:**
    This interface defines the API endpoint. The `@Streaming` annotation is crucial for receiving results incrementally as the model generates them, allowing for real-time updates on the screen.

    ```kotlin
    interface ApiStreamingService {
        @POST("api/generate")
        @Streaming
        suspend fun generate(
            @Body request: Any
        ): Response<ResponseBody>
    }
    ```

2.  **The Hilt Network Module:**
    This module, using Hilt for dependency injection, sets up the Retrofit instance required to make network calls to the Ollama server.

    *   **Important:** Replace `192.168.1.92` with the actual IP address of your machine running the Ollama server (you can find this using `hostname -I` on Linux/macOS or `ipconfig` on Windows).
    *   The default Ollama port is `11434`.
    *   Since the local server typically uses `http` (not `https`), you need to allow cleartext traffic in your Android app's manifest (`AndroidManifest.xml`) by adding `android:usesCleartextTraffic="true"` to the `<application>` tag.

    ```kotlin
    @Module
    @InstallIn(SingletonComponent::class)
    object NetworkModule {
        // On the host machine do a "hostname -I" to check the IP
        // In my case it was 192.168.1.92
        // Port for Jetson Orin Nano is 11434
        // Since we use http for the local server then use android:usesCleartextTraffic="true" at the manifest
        private const val BASE_URL = "http://192.168.1.92:11434/" // <--- Make sure this IP is correct!

        @Provides
        @Singleton
        fun provideRetrofit(): Retrofit {
            // Increased timeouts for potentially long model generations
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create()) // Using Gson for JSON parsing
                .build()
        }

        // Example of providing a non-streaming service (commented out)
        /*@Provides
        @Singleton
        fun provideApiService(retrofit: Retrofit): ApiService =
            retrofit.create(ApiService::class.java)*/

        // Provide the streaming service instance
        @Provides
        @Singleton
        fun provideApiService(retrofit: Retrofit): ApiStreamingService =
            retrofit.create(ApiStreamingService::class.java)
    }
    ```

3.  **The `processStream()` Function (ViewModel):**
    This function, likely within your ViewModel, handles the incoming `ResponseBody` from the streaming API call. It reads the stream line by line, parses the JSON response fragment (assuming each line is a JSON object containing part of the response), and updates the UI state (e.g., a `StateFlow` or `MutableState`) on the main thread.

    ```kotlin
    private suspend fun processStream(responseBody: ResponseBody) {
            // Wrap the byte stream with a BufferedReader for easy line reading.
            responseBody.byteStream().bufferedReader().use { reader: BufferedReader ->
                // Loop indefinitely until the stream is closed (readLine() returns null).
                while (true) {
                    val line = reader.readLine() ?: break // Read one line (JSON object)
                    // Switch to the Main dispatcher to safely update UI state.
                    withContext(Dispatchers.Main) {
                        Log.v("streaming_", line) // Log the raw line for debugging
                        // Assuming JsonParser.parseResponse extracts the text chunk from the line
                        _serverResult.value += JsonParser.parseResponse(line)
                        // Optionally update a flag indicating the server is no longer processing
                        updateJetsonIsWorking(false)
                    }
                }
            }
        }
    ```

You can easily customize the application built with these components to use any of the available Gemma 3 models (or other models) hosted by your local Ollama server by changing the `"model"` parameter in your request body.

Check a full guide on [Medium](https://medium.com/p/d1649b5300a4)
