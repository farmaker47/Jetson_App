package com.example.jetsonapp

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetsonapp.internet.KokoroService
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltViewModel
class JetsonViewModel @javax.inject.Inject constructor(
    application: Application,
    private val kokoroService: KokoroService
) :
    AndroidViewModel(application) {

    private val context = application
    private var mediaPlayer: MediaPlayer? = null
    private val whisperEngine: IWhisperEngine = WhisperEngine(context)
    private val recorder: Recorder = Recorder(context)
    private val outputFileWav = File(application.filesDir, RECORDING_FILE_WAV)
    private val _userPrompt = MutableStateFlow("")
    private val userPrompt = _userPrompt.asStateFlow()
    private fun updateUserPrompt(newValue: String) {
        _userPrompt.value = newValue
    }

    private val _vlmResult = MutableStateFlow("")
    val vlmResult = _vlmResult.asStateFlow()
    fun updateVlmResult(newValue: String) {
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

    private lateinit var generativeModel: GenerativeModel
    private lateinit var session: LlmInferenceSession

    fun initialize() {
        updateJetsonIsWorking(true)
        // Initialize generativeModel and session here
        viewModelScope.launch(Dispatchers.IO) {
            generativeModel = createGenerativeModel()
            session = createSession(context)
            updateJetsonIsWorking(false)
        }
    }

    init {
        whisperEngine.initialize(MODEL_PATH, getAssetFilePath(context = context), false)
        recorder.setFilePath(getFilePath(context = context))
    }

    fun startRecordingWav() {
        recorder.start()
    }

    fun stopRecordingWav() {
        recorder.stop()

        try {
            viewModelScope.launch(Dispatchers.IO) {
                // Offline speech to text
                val transcribedText = whisperEngine.transcribeFile(outputFileWav.absolutePath)
                Log.v("transription", transcribedText)

                updateUserPrompt(transcribedText)
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
                val chat = generativeModel.startChat()
                val response = chat.sendMessage(userPrompt.value)
                Log.v("function", "Model response: $response")

                if (response.candidatesCount > 0 && response.getCandidates(0).content.partsList.size > 0) {
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
                            }

                            else -> {
                                Log.v("function", "getCameraImage")
                                throw Exception("Function does not exist:" + functionCall.name)
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
                    }
                } else {
                    Log.v("function_else_if", "no parts")
                }
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, e.toString())
        } catch (e: IllegalStateException) {
            Log.e(TAG, e.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Model Error: ${e.message}", e)
        }
    }

    // private val generativeModel by lazy { createGenerativeModel() }
    // private val session by lazy { createSession(context) }
    private var selectedImage = ""

    fun updateSelectedImage(context: Context, uri: Uri) {
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
            }

            base64
        } catch (e: Exception) {
            Log.e("ImageUtils", "Error processing image", e)
            ""
        }
    }

    fun convertBitmapToBase64(bitmap: Bitmap) {
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
        // Convert the input Bitmap object to an MPImage object to run inference
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Process the bitmap (if needed) using BitmapImageBuilder
                val mpImage = BitmapImageBuilder(bitmap).build()
                session.addQueryChunk(userPrompt.value)
                session.addImage(mpImage)

                var stringBuilder = ""
                session.generateResponseAsync({ partialResult, done ->
                    stringBuilder += partialResult
                    Log.v("image_partial", "$stringBuilder $done")
                    updateVlmResult(stringBuilder)
                })

                session.close()
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
        val tool = Tool.newBuilder()
            .addFunctionDeclarations(getCameraImage)
            .build()

        val formatter =
            HammerFormatter(ModelFormatterOptions.builder().setAddPromptTemplate(true).build())

        val llmInferenceOptions = LlmInferenceOptions.builder()
            .setModelPath("/data/local/tmp/Hammer2.1-1.5b_seq128_q8_ekv1280.task") // hammer2.1_1.5b_q8_ekv4096.task or gemma-3n-E2B-it-int4.task
            .setMaxTokens(512)                                              // Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task
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
                    .setText("This assistant will help you to open the camera")
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
            .setMaxTokens(2024) // Default
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

    /** Streams the WAV bytes into Downloads and returns the URI. */
    private fun saveWavToDownloads(body: ResponseBody): Uri {
        val fileName = "generated_${System.currentTimeMillis()}.mp3"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // MediaStore on Android 10+
            val resolver = context.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "audio/mp3")
            }
            val uri = resolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IOException("Failed to create MediaStore record")
            resolver.openOutputStream(uri)!!.use { out ->
                body.byteStream().copyTo(out)
            }
            uri
        } else {
            // Legacy path on < Android 10
            val downloads = File(
                Environment.getExternalStorageDirectory(),
                Environment.DIRECTORY_DOWNLOADS
            ).apply { if (!exists()) mkdirs() }
            val outFile = File(downloads, fileName)
            FileOutputStream(outFile).use { out ->
                body.byteStream().copyTo(out)
            }
            Uri.fromFile(outFile)
        }
    }

    /** Sets up MediaPlayer, prepares async and starts playback when ready. */
    private fun playAudio(uri: Uri) {
        stopAudio()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            prepare()
            start()
        }
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.reset()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
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
}
