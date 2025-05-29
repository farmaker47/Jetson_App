package com.example.jetsonapp

import android.app.Application
import android.content.ContentValues
import android.content.Context
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
import com.example.jetsonapp.internet.GenerateImageRequest
import com.example.jetsonapp.internet.GenerateRequest
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
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Suppress("IMPLICIT_CAST_TO_ANY")
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

    private val _serverResult = MutableStateFlow("")
    val serverResult = _serverResult.asStateFlow()
    fun updateServerResult(newValue: String) {
        _serverResult.value = newValue
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
//                                                    Log.v("else_if_function", "getCameraImage")
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
                val response = chat.sendMessage(transcribedText)
                Log.v("else_if_function", "Model response: $response")

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
                                Log.v("else_if_function", "getCameraImage")
                            }

                            else -> throw Exception("Function does not exist:" + functionCall.name)
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
                        Log.v("else_if", message.text)
                        Log.v("else_if", extractFunctionName(message.text) ?: "no function")
                    }
                } else {
                    Log.v("else_if", "no parts")
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

    private var selectedImage = ""
    fun updateSelectedImage(context: Context, uri: Uri) {
        selectedImage = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private val generativeModel by lazy { createGenerativeModel() }

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
            .setModelPath("/data/local/tmp/hammer2.1_1.5b_q8_ekv4096.task") // hammer2.1_1.5b_q8_ekv4096.task or gemma-3n-E2B-it-int4.task
            .setMaxTokens(2048)                                             // SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task
            .apply { setPreferredBackend(Backend.GPU) }
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

    private suspend fun sendData() {
        updateJetsonIsWorking(true)
        // For streaming purposes IO dispatcher is preferred
        val time = System.currentTimeMillis()
        try {
            val request = if (selectedImage.isEmpty()) {
                GenerateRequest(
                    prompt = userPrompt.value + TWENTY_WORDS,
                    stream = false
                )
            } else {
                GenerateImageRequest(
                    prompt = userPrompt.value + TWENTY_WORDS,
                    stream = false,
                    images = listOf(selectedImage)
                )
            }
            val kokoroResponse = kokoroService.generate(request)
            if (kokoroResponse.isSuccessful && kokoroResponse.body() != null) {
                Log.v("time_total_describe", (System.currentTimeMillis() - time).toString())
                val uri = saveWavToDownloads(kokoroResponse.body()!!)
                playAudio(uri)
            } else {
                updateServerResult("Error: ${kokoroResponse.code()} - ${kokoroResponse.message()}")
            }
        } catch (e: IOException) {
            updateServerResult("Network error: ${e.message}")
        } catch (e: Exception) {
            updateServerResult("Unexpected error: ${e.message}")
        } finally {
            updateJetsonIsWorking(false)
            // selectedImage = ""
        }

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
        private const val MODEL = "gemma3:4b"
        private const val DUMMY_IMAGE_BASE_64 =
            "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"

        private const val TWENTY_WORDS = "in 20 words"
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
