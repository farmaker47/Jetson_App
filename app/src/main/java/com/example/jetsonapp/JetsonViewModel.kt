package com.example.jetsonapp

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetsonapp.recorder.Recorder
import com.example.jetsonapp.utils.CameraUtil.extractFunctionName
import com.example.jetsonapp.whisperengine.WhisperEngineNative
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil.runOnUiThread
import com.facebook.react.bridge.WritableMap
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.HammerFormatter
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.ai.edge.localagents.fc.ModelFormatterOptions
import com.google.gson.Gson
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.rnllama.RNLlama
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

@HiltViewModel
class JetsonViewModel @javax.inject.Inject constructor(
    application: Application
) :
    AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context = application
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var rnLlama: RNLlama
    private var isLlamaReady by mutableStateOf(false)
    private var isCompleting by mutableStateOf(false)
    private var llamaStatus by mutableStateOf("Initializing...")
    private var completionResult by mutableStateOf("")
    private var isMultimodalReady by mutableStateOf(false)
    private var tokensPerSecond by mutableIntStateOf(0)
    private val whisperEngine = WhisperEngineNative()
    // private val whisperEngine: IWhisperEngine = WhisperEngine(context)
    private val recorder: Recorder = Recorder(context)
    private val outputFileWav = File(application.filesDir, RECORDING_FILE_WAV)
    private val languageIdentifier = LanguageIdentification.getClient()
    private lateinit var textToSpeech: TextToSpeech
    private var transcribedText = ""
    private val _userPrompt = MutableStateFlow("")
    private val userPrompt = _userPrompt.asStateFlow()
    private fun updateUserPrompt(newValue: String) {
        _userPrompt.value = newValue
    }

    private val _vlmResult = MutableStateFlow("")
    val vlmResult = _vlmResult.asStateFlow()
    private fun updateVlmResult(newValue: String) {
        _vlmResult.value = newValue
    }

    private val _jetsonIsWorking = MutableStateFlow(false)
    val jetsonIsWorking = _jetsonIsWorking.asStateFlow()
    private fun updateJetsonIsWorking(newValue: Boolean) {
        _jetsonIsWorking.value = newValue
    }

    private val _microphoneIsRecording = MutableStateFlow(false)
    val microphoneIsRecording = _microphoneIsRecording.asStateFlow()
    fun updateMicrophoneIsRecording(newValue: Boolean) {
        _microphoneIsRecording.value = newValue
    }

    private val _cameraFunctionTriggered = MutableStateFlow(false)
    val cameraFunctionTriggered = _cameraFunctionTriggered.asStateFlow()
    fun updateCameraFunctionTriggered(newValue: Boolean) {
        _cameraFunctionTriggered.value = newValue
    }

    private val _phoneGalleryTriggered = MutableStateFlow(false)
    val phoneGalleryTriggered = _phoneGalleryTriggered.asStateFlow()
    fun updatePhoneGalleryTriggered(newValue: Boolean) {
        _phoneGalleryTriggered.value = newValue
    }

    private var generativeModel: GenerativeModel? = null
    private var session: LlmInferenceSession? = null

    private fun initialize() {
        updateJetsonIsWorking(true)

        val reactContext = ReactApplicationContext(context)
        rnLlama = RNLlama(reactContext)

        // Initialize generativeModel and session here
        viewModelScope.launch(Dispatchers.IO) {
            generativeModel = createGenerativeModel()
            initializeVisionContext()
            updateJetsonIsWorking(false)
        }
    }

    init {
        // whisperEngine.initialize(MODEL_PATH, getAssetFilePath(context = context), true) // true
        whisperEngine.initialize(
            getAssetFilePath(MODEL_PATH, context) ?: "",
            getAssetFilePath(VOCAB_PATH, context) ?: "",
            true
        )
        recorder.setFilePath(getFilePath(context = context))

        initialize()
    }

    fun startRecordingWav() {
        recorder.start()
    }

    fun stopRecordingWav() {
        recorder.stop()
        updateVlmResult("")
        updateJetsonIsWorking(true)

        try {
            viewModelScope.launch(Dispatchers.IO) {
                // Offline speech to text
                transcribedText = whisperEngine.transcribeFile(outputFileWav.absolutePath).toString()
                Log.v("transription", transcribedText.trim())

                updateUserPrompt(transcribedText.trim())
                updateVlmResult(transcribedText.trim())

                // Example from the Google's function calling app
                // https://github.com/google-ai-edge/ai-edge-apis/tree/main/examples/function_calling/healthcare_form_demo
                // Conversion instructions
                // https://github.com/google-ai-edge/ai-edge-torch/tree/main/ai_edge_torch/generative/examples
                // Speech recognition example
                // https://medium.com/@andraz.pajtler/android-speech-to-text-the-missing-guide-part-1-824e2636c45a

                // Extract the model's message from the response.
                val chat = generativeModel?.startChat()
                val response = chat?.sendMessage(userPrompt.value)
                Log.v("function", "Model response: $response")

                if (response != null && response.candidatesCount > 0 && response.getCandidates(0).content.partsList.size > 0) {
                    val message = response.getCandidates(0).content.getParts(0)

                    // If the message contains a function call, execute the function.
                    if (message.hasFunctionCall()) {
                        val functionCall = message.functionCall

                        // Call the appropriate function.
                        when (functionCall.name) {
                            "getCameraImage" -> {
                                Log.v("function", "getCameraImage")
                                _cameraFunctionTriggered.value = true
                                updateJetsonIsWorking(false)
                            }

                            "openPhoneGallery" -> {
                                Log.v("function", "openPhoneGallery")
                                _phoneGalleryTriggered.value = true
                                updateJetsonIsWorking(false)
                            }

                            else -> {
                                Log.e("function", "no function to call")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "No function to call, say something like \"open the camera\"",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                updateJetsonIsWorking(false)
                                // throw Exception("Function does not exist:" + functionCall.name)
                            }
                        }
                    } else if (message.hasText()) {
                        Log.v("function_else_if", message.text)
                        Log.v(
                            "function_else_if",
                            extractFunctionName(message.text) ?: "no function"
                        )
                        if (extractFunctionName(message.text) == "getCameraImage") {
                            _cameraFunctionTriggered.value = true
                            updateJetsonIsWorking(false)
                        } else if (extractFunctionName(message.text) == "openPhoneGallery") {
                            _phoneGalleryTriggered.value = true
                            updateJetsonIsWorking(false)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "No function to call, say something like \"open the camera\"",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Log.v("function_else_if", "no parts")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "No function to call, say something like \"open the camera\"",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    updateJetsonIsWorking(false)
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, e.toString())
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Model Error: ${e.message}", e)
        } finally {
            // updateJetsonIsWorking(false)
        }
    }

    private var selectedImage = ""

    fun updateSelectedImage(context: Context, uri: Uri) {
        updateJetsonIsWorking(true)
        selectedImage = try {
            val contentResolver = context.contentResolver

            // Convert Uri to Base64 string
            val base64 = contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""

            // Create a bitmap from the Uri and log its width and height
            contentResolver.openInputStream(uri)?.use { bmpStream ->
                val bitmap = BitmapFactory.decodeStream(bmpStream)
                bitmap?.let {
                    Log.d("ImageInfo", "Bitmap width: ${it.width}, height: ${it.height}")
                }
                // VLM procedure
                //inferenceVLM(bitmap)
            }

            base64
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error processing image", e)
            ""
        }
        startVisionCompletion()
    }

    fun convertBitmapToBase64(bitmap: Bitmap) {
        updateJetsonIsWorking(true)
        selectedImage = try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            bitmap.let {
                Log.d("ImageInfo", "Bitmap width: ${it.width}, height: ${it.height}")
            }
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }

        // VLM procedure
        startVisionCompletion()
        //inferenceVLM(bitmap)
    }

    private fun inferenceVLM(bitmap: Bitmap) {
        var chunkCounter = 0
        viewModelScope.launch(Dispatchers.IO) {
            val chunkBuffer = StringBuilder()
            try {
                textToSpeech = TextToSpeech(context, this@JetsonViewModel)
                // Convert the input Bitmap object to an MPImage object to run inference
                // Process the bitmap (if needed) using BitmapImageBuilder
                val mpImage = BitmapImageBuilder(bitmap).build()
                session?.addQueryChunk(userPrompt.value + " in 20 words") // Limit if you do not want a vast output.
                session?.addImage(mpImage)

                var stringBuilder = ""
                session?.generateResponseAsync { chunk, done ->
                    updateJetsonIsWorking(false)
                    stringBuilder += chunk
                    // Log.v("image_partial", "$stringBuilder $done")
                    updateVlmResult(transcribedText.trim() + "\n\n" + stringBuilder)

                    // Speak the chunks
                    chunkBuffer.append(chunk)
                    chunkCounter++

                    // Check if 7 chunks have been collected
                    if (chunkCounter == 7) {
                        // Speak out the combined text of the last 7 chunks
                        speakOut(chunkBuffer.toString())
                        Log.v("finished_main", chunkBuffer.toString())

                        // Reset the buffer and the counter for the next group of chunks
                        chunkBuffer.clear()
                        chunkCounter = 0
                    }
                }

                if (chunkBuffer.isNotEmpty()) {
                    speakOut(chunkBuffer.toString())
                    Log.v("finished_main", chunkBuffer.toString())
                }

                session?.close()
            } catch (e: Exception) {
                Log.e("image_exception", e.message.toString())
            }
        }
    }

    private fun createGenerativeModel(): GenerativeModel {
        val getCameraImage = FunctionDeclaration.newBuilder()
            .setName("getCameraImage")
            .setDescription("Function to open the camera")
            .build()
        val openPhoneGallery = FunctionDeclaration.newBuilder()
            .setName("openPhoneGallery")
            .setDescription("Function to open the gallery")
            .build()
        val tool = Tool.newBuilder()
            .addFunctionDeclarations(getCameraImage)
            .addFunctionDeclarations(openPhoneGallery)
            .build()

        val formatter =
            HammerFormatter(ModelFormatterOptions.builder().setAddPromptTemplate(true).build())

        val llmInferenceOptions = LlmInferenceOptions.builder()
            // hammer2.1_1.5b_q8_ekv4096.task
            // gemma-3n-E2B-it-int4.task
            .setModelPath("/data/local/tmp/hammer2p1_05b_seb.task")
            .setMaxTokens(512)
            .apply { setPreferredBackend(Backend.CPU) }
            .build()

        val llmInference =
            LlmInference.createFromOptions(context, llmInferenceOptions)
        val llmInferenceBackend =
            LlmInferenceBackend(llmInference, formatter)

        val systemInstruction = Content.newBuilder()
            .setRole("system")
            .addParts(
                Part.newBuilder()
                    .setText(
                        "You are a helpful assistant.\n" +
                                "\n" +
                                "When the user provides input in a language other than English, your first task is to translate it into English before generating a response.\n" +
                                "\n" +
                                "If the input is already in English, proceed directly without translating.\n" +
                                "\n" +
                                "You can also open the camera or the phone gallery when requested to do.\n" +
                                "\n" +
                                "Always ensure the user’s request is understood in English before taking any action or providing a response"
                    )
            )
            .build()

        val model = GenerativeModel(
            llmInferenceBackend,
            systemInstruction,
            listOf(tool).toMutableList()
        )
        return model
    }

    private fun createSession(context: Context): LlmInferenceSession {
        // Configure inference options and create the inference instance
        val options = LlmInferenceOptions.builder()
            .setModelPath("/data/local/tmp/gemma-3n-E2B-it-int4.task")
            .setMaxTokens(1024)
            .setPreferredBackend(Backend.GPU)
            .setMaxNumImages(1)
            .build()
        val llmInference = LlmInference.createFromOptions(context, options)

        // Configure session options and create the session
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40) // Default
            .setTopP(0.9f)
            .setTemperature(1.0f)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech.stop()
        textToSpeech.shutdown()
        languageIdentifier.close()
        whisperEngine.freeModel()
    }

    companion object {

        private const val MODEL_PATH = "whisper-base_translate_default_quant.tflite"
        private const val VOCAB_PATH = "filters_vocab_multilingual.bin"
        private const val RECORDING_FILE_WAV = "recording.wav"
        private const val TAG = "JetsonViewModel"
    }

    // Returns file path for vocab .bin file
    private fun getFilePath(assetName: String = RECORDING_FILE_WAV, context: Context): String? {
        val outfile = File(context.filesDir, assetName)
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.absolutePath)
        }
        Log.d(TAG, "Returned asset path: " + outfile.absolutePath)
        return outfile.absolutePath
    }

    private fun getAssetFilePath(assetName: String = VOCAB_PATH, context: Context): String? {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            try {
                context.assets.open(assetName).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
        return file.absolutePath
    }

    fun stopGenerating() {
        session?.cancelGenerateResponseAsync()
        updateJetsonIsWorking(false)
    }

    // Function to use for Text-to-Speech.
    private fun speakOut(text: String) {
        val defaultLocale = Locale("en")
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val locale = if (languageCode == "und") defaultLocale else Locale(languageCode)
                textToSpeech.setLanguage(locale)
                // Log.v("available_languages", textToSpeech.availableLanguages.toString())
                textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, "speech_utterance_id")
            }
            .addOnFailureListener {
                textToSpeech.setLanguage(defaultLocale)
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speech_utterance_id")
            }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // TTS initialization successful
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                }

                override fun onDone(utteranceId: String?) {
                    updateJetsonIsWorking(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    updateJetsonIsWorking(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(p0: String?) {
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    updateJetsonIsWorking(false)
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                }
            })
        } else {
            // TTS initialization failed
            Log.e("TTS", "Initialization failed")
        }
    }

    // Multimodal VLMs
    private fun initializeVisionContext() {
        // --- MODIFIED: Add ctx_shift: false for multimodal ---
        val params = Arguments.createMap().apply {
            putString("model", "/data/local/tmp/SmolVLM2-500M-Video-Instruct-Q8_0.gguf") // Llama-3.2-1B-Instruct-Q8_0.gguf
            putInt("n_ctx", 4096)
            // putInt("n_gpu_layers", 99)
            putBoolean("ctx_shift", false) // Crucial for multimodal models
        }

        val promise = object : Promise {
            override fun resolve(value: Any?) {
                Log.d("Llama init", "Llama context initialized successfully!")
                runOnUiThread {
                    llamaStatus = "Main context loaded! Initializing multimodal projector..."
                    isLlamaReady = true

                    initializeMultimodal()
                }
            }

            override fun reject(code: String?, message: String?) {

            }

            override fun reject(code: String?, throwable: Throwable?) {
            }

            override fun reject(code: String?, message: String?, e: Throwable?) {
                Log.e("Llama init", "Failed to initialize Llama context: $message", e)
                runOnUiThread { llamaStatus = "Error: Failed to load Llama context.\n${message}" }
            }

            override fun reject(throwable: Throwable?) {
            }

            override fun reject(throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, userInfo: WritableMap) {
            }

            override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, message: String?, userInfo: WritableMap) {
            }

            override fun reject(
                code: String?,
                message: String?,
                throwable: Throwable?,
                userInfo: WritableMap?
            ) {
            }

            override fun reject(message: String?) {
            }
        }

        llamaStatus = "Loading model: VLM..."
        rnLlama.initContext(1.0, params, promise)
    }

    private fun initializeMultimodal() {

        val params = Arguments.createMap().apply {
            putString("path", "/data/local/tmp/mmproj-SmolVLM2-500M-Video-Instruct-Q8_0.gguf") // mmproj-ultravox-v0_5-llama-3_2-1b-f16.gguf
        }

        val promise = object : Promise {
            override fun resolve(value: Any?) {
                if (value as Boolean) {
                    Log.d("Llama init", "Multimodal support initialized successfully!")
                    runOnUiThread {
                        llamaStatus = "Model and projector loaded. Ready!"
                        isMultimodalReady = true
                    }
                } else {
                    reject(null, "initMultimodal returned false.", null)
                }
            }

            override fun reject(code: String?, message: String?) {

            }

            override fun reject(code: String?, throwable: Throwable?) {
            }

            override fun reject(code: String?, message: String?, e: Throwable?) {
                Log.e("Llama init", "Failed to init multimodal: $message", e)
                runOnUiThread {
                    llamaStatus = "Error: Failed to init multimodal projector.\n$message"
                }
            }

            override fun reject(throwable: Throwable?) {
            }

            override fun reject(throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, userInfo: WritableMap) {
            }

            override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, message: String?, userInfo: WritableMap) {
            }

            override fun reject(
                code: String?,
                message: String?,
                throwable: Throwable?,
                userInfo: WritableMap?
            ) {
            }

            override fun reject(message: String?) {
            }
        }

        rnLlama.initMultimodal(1.0, params, promise)
    }

    private fun startVisionCompletion() {
        if (!isMultimodalReady || isCompleting) return

        isCompleting = true
        completionResult = ""
        llamaStatus = "Formatting vision prompt..."

//        val messages = listOf(
//            mapOf(
//                "role" to "user",
//                "content" to "What do you see in this image? Describe it in detail.\n<__media__>"
//            )
//
//        )
//        val messages2 = listOf(
//            mapOf("role" to "system", "content" to "You are a helpful assistant."),
//            mapOf("role" to "user", "content" to "What do you see in this image? Describe it in detail.")
//        )

        val messages2 = listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to "What do you see in this image? Describe it in detail.\n<__media__>"
                    ),
                    /*mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf(
                            "url" to "file:///data/local/tmp/bike_896.png"
                            // Or: "base64" to "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAYABgAAD..."
                        )
                    )*/
                )
            )
        )
        val messagesJson = Gson().toJson(messages2)

        val formatParams = Arguments.createMap()

        val formatPromise = object : Promise {
            override fun resolve(value: Any?) {
                val formattedPrompt = value as String
                Log.d("Llama Vision", "Formatted Prompt: $formattedPrompt")

                runOnUiThread { llamaStatus = "Generating description..." }

                // Format the media to the expected format
                "data:image/png;base64,$selectedImage"

                runVisionCompletion(
                    formattedPrompt,
                    "data:image/png;base64,$selectedImage"
                )


                // OR if you have an image file
                /*encodeFileToBase64DataUri("/data/local/tmp/lightning.png")?.let {
                    runVisionCompletion(
                        formattedPrompt,
                        it
                    )
                }*/

                // OR
                /*runVisionCompletion(
                    formattedPrompt,
                    "/data/local/tmp/bike_896.png"
                )*/
            }

            override fun reject(code: String?, message: String?) {

            }

            override fun reject(code: String?, throwable: Throwable?) {
            }

            override fun reject(code: String?, message: String?, e: Throwable?) {
                Log.e("Llama Vision", "Failed to format chat: $message", e)
                runOnUiThread { isCompleting = false; llamaStatus = "Error formatting prompt." }
            }

            override fun reject(throwable: Throwable?) {
            }

            override fun reject(throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, userInfo: WritableMap) {
            }

            override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, message: String?, userInfo: WritableMap) {
            }

            override fun reject(
                code: String?,
                message: String?,
                throwable: Throwable?,
                userInfo: WritableMap?
            ) {
            }

            override fun reject(message: String?) {
            }
        }

        rnLlama.getFormattedChat(1.0, messagesJson, null, formatParams, formatPromise)

        // Just to get information about the multimodal capabilities
        // Use with some modifications above
        // rnLlama.getMultimodalSupport(1.0, formatPromise)
        // Get some info
        // rnLlama.modelInfo()
    }


    private fun runVisionCompletion(prompt: String, imageFile: String) {
        val stopWords = Arguments.fromList(listOf("</s>", "\n", "User:", "<end_of_utterance>"))

        val completionParams = Arguments.createMap().apply {
            putString("prompt", prompt)
            putInt("n_predict", 100)
            putArray("stop", stopWords)
            putDouble("temperature", 0.1)

            // If an image file is provided, add it to the media_paths array
            val mediaPaths = Arguments.createArray()
            mediaPaths.pushString(imageFile)
            putArray("media_paths", mediaPaths)
        }

        val streamCallback = RNLlama.StreamCallback { token ->
            // Append the new token to our result state
            completionResult += token
            Log.d("Llama Stream", "Streaming: $completionResult")
        }

        val completionPromise = object : Promise {
            override fun resolve(value: Any?) {
                val result = value as WritableMap
                val resultText = result.getString("text") ?: "No text in result"
                val timings = result.getMap("timings")

                val tps = timings?.getDouble("predicted_per_second") ?: 0.0
                tokensPerSecond = tps.roundToInt()

                Log.d("Llama Chat", "Completion finished.")
                Log.d("Llama Chat", "Result text: $resultText")
                if (timings != null) {
                    Log.d(
                        "Llama Chat",
                        "Timings: Predicted tokens: ${timings.getInt("predicted_n")} in ${
                            timings.getInt("predicted_ms")
                        } ms"
                    )
                }

                Log.d("Llama Chat", "Completion finished.")
                runOnUiThread {
                    completionResult = resultText.trim()
                    llamaStatus = "Completed!"
                    isCompleting = false
                }
            }

            override fun reject(code: String?, message: String?) {
            }

            override fun reject(code: String?, throwable: Throwable?) {
            }

            override fun reject(code: String?, message: String?, e: Throwable?) {
                Log.e("Llama Chat", "Completion failed: $message", e)
                runOnUiThread {
                    llamaStatus = "Error during completion: $message"
                    isCompleting = false
                }
            }

            override fun reject(throwable: Throwable?) {
            }

            override fun reject(throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, userInfo: WritableMap) {
            }

            override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap?) {
            }

            override fun reject(code: String?, message: String?, userInfo: WritableMap) {
            }

            override fun reject(
                code: String?,
                message: String?,
                throwable: Throwable?,
                userInfo: WritableMap?
            ) {
            }

            override fun reject(message: String?) {
            }
        }

        rnLlama.completionStream(1.0, completionParams, streamCallback, completionPromise)
        // OR
        // rnLlama.completion(1.0, completionParams, completionPromise)
    }

    private fun encodeFileToBase64DataUri(filePath: String): String? {
        return try {
            val file = File(filePath)

            if (!file.exists() || !file.canRead()) {
                Log.e("Base64", "File does not exist or cannot be read: $filePath")
                llamaStatus = "Error: Could not read file from path."
                return null
            }

            val bytes = file.readBytes()
            val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val mimeType = when (filePath.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                // "mp4" -> "video/mp4"
                // "pdf" -> "application/pdf"
                else -> "application/octet-stream" // fallback
            }

            "data:$mimeType;base64,$base64String"
        } catch (e: Exception) {
            Log.e("Base64", "Error encoding file to base64 from path: $filePath", e)
            llamaStatus = "Error: Could not encode file."
            null
        }
    }
}
