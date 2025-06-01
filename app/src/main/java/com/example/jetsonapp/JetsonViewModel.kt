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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetsonapp.recorder.Recorder
import com.example.jetsonapp.utils.CameraUtil.extractFunctionName
import com.example.jetsonapp.whisperengine.IWhisperEngine
import com.example.jetsonapp.whisperengine.WhisperEngine
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.HammerFormatter
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.ai.edge.localagents.fc.ModelFormatterOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mlkit.nl.languageid.LanguageIdentification
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

@HiltViewModel
class JetsonViewModel @javax.inject.Inject constructor(
    application: Application
) :
    AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context = application
    private var mediaPlayer: MediaPlayer? = null
    private val whisperEngine: IWhisperEngine = WhisperEngine(context)
    private val recorder: Recorder = Recorder(context)
    private val outputFileWav = File(application.filesDir, RECORDING_FILE_WAV)
    private val languageIdentifier = LanguageIdentification.getClient()
    private lateinit var textToSpeech: TextToSpeech
    private var transcribedText = ""
    private val _userPrompt = MutableStateFlow("")
    val userPrompt = _userPrompt.asStateFlow()
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
        // Initialize generativeModel and session here
        viewModelScope.launch(Dispatchers.IO) {
            generativeModel = createGenerativeModel()
            updateJetsonIsWorking(false)
            session = createSession(context)
        }
    }

    init {
        whisperEngine.initialize(MODEL_PATH, getAssetFilePath(context = context), false)
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
                transcribedText = whisperEngine.transcribeFile(outputFileWav.absolutePath)
                Log.v("transription", transcribedText.trim())

                updateUserPrompt(transcribedText.trim())
                updateVlmResult(transcribedText.trim())
                // sendData()

                // Example from the Google's function calling app
                // https://github.com/google-ai-edge/ai-edge-apis/tree/main/examples/function_calling/healthcare_form_demo
                // Conversion instructions
                // https://github.com/google-ai-edge/ai-edge-torch/tree/main/ai_edge_torch/generative/examples
                // Speech recognition example
                // https://medium.com/@andraz.pajtler/android-speech-to-text-the-missing-guide-part-1-824e2636c45a
//                try {
//                    val chat = generativeModel.startChat()
//                    val response = chat.sendMessage(transcribedText)
//                    Log.d(TAG, "Hammer Response: $response") // Log response
//
//                    response.getCandidates(0).content.partsList?.let { parts ->
//                        try {
//                            // extract the function from all the parts in the response
//                            parts.forEach { part ->
//                                part?.functionCall?.args?.fieldsMap?.forEach { (key, value) ->
//                                    value.stringValue?.let { stringValue ->
//                                        if (stringValue != "<unknown>") {
//                                            when (key) {
//                                                "getCameraImage" -> {
//                                                    Log.v("function", "getCameraImage")
//                                                }
//
//                                                else -> {
//                                                    throw Exception("Unknown function: $key value: $value")
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        } catch (e: JSONException) {
//                            Log.e(TAG, "JSON Parsing Error: ${e.message}")
//                        } finally {
//                            Log.d(TAG, "STOP PROCESSING")
//                        }
//                    } ?: run {
//                        Log.e(TAG, "Model response was null")
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "Model Error: ${e.message}", e)
//                    val errorMessage = if (e is IndexOutOfBoundsException) {
//                        "The model returned no response to \"$transcribedText\"."
//                    } else {
//                        e.localizedMessage
//                    }
//                } finally {
//                    Log.i(TAG, "Model processing ended")
//                }

                // Extract the model's message from the response.
                val chat = generativeModel?.startChat()
                val response = chat?.sendMessage(userPrompt.value)
                Log.v("function", "Model response: $response")

                if (response != null && response.candidatesCount > 0 && response.getCandidates(0).content.partsList.size > 0) {
                    val message = response.getCandidates(0).content.getParts(0)

                    // If the message contains a function call, execute the function.
                    if (message.hasFunctionCall()) {
                        val functionCall = message.functionCall
                        // val args = functionCall.args.fieldsMap
                        // var result = null

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
                        // Return the result of the function call to the model.
                        /*val functionResponse =
                            FunctionResponse.newBuilder()
                                .setName(functionCall.getName())
                                .setResponse(
                                    Struct.newBuilder()
                                        .putFields(
                                            "result",
                                            Value.newBuilder().setStringValue(result).build()
                                        )
                                )
                                .build()
                        val response = chat.sendMessage(functionResponse)*/
                    } else if (message.hasText()) {
                        Log.v("function_else_if", message.text)
                        Log.v(
                            "function_else_if",
                            extractFunctionName(message.text) ?: "no function"
                        )
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

    // private val generativeModel by lazy { createGenerativeModel() }
    // private val session by lazy { createSession(context) }
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
                inferenceVLM(bitmap)
            }

            base64
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error processing image", e)
            ""
        }
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
        inferenceVLM(bitmap)
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
                    // val cleanText = partialResult.replace("\n", ". ")
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
            // hammer2.1_1.5b_q8_ekv4096.task -> crashing
            // gemma-3n-E2B-it-int4.task
            // Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task
            .setModelPath("/data/local/tmp/Hammer2.1-1.5b_seq128_q8_ekv1280.task")
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
                    .setText("You are a helpful assistant that will open the camera or the phone gallery.")
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
    }

    companion object {
        private const val MODEL_PATH = "whisper_tiny_en_14.tflite"
        private const val VOCAB_PATH = "filters_vocab_en.bin"
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
}
