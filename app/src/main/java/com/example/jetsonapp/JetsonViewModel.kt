package com.example.jetsonapp

import android.app.Application
import android.content.ContentValues
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.MediaStore
import kotlinx.coroutines.withContext
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetsonapp.internet.ApiStreamingService
import com.example.jetsonapp.internet.GenerateImageRequest
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
    private val apiService: ApiStreamingService // change for non streaming flows
) :
    AndroidViewModel(application) {

    private val _userPrompt = MutableStateFlow("")
    private val userPrompt = _userPrompt.asStateFlow()
    private val context = application
    fun updateUserPrompt(newValue: String) {
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
    private var mediaPlayer: MediaPlayer? = null

    fun sendData() {
        updateJetsonIsWorking(true)
        // For streaming purposes IO dispatcher is preferred
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = GenerateImageRequest(
                    prompt = userPrompt.value,
                    voice = "af_heart"
                )
                val response = apiService.generate(request)
                if (response.isSuccessful && response.body() != null) {
                    val uri = saveWavToDownloads(response.body()!!)
                    playAudio(uri)
                } else {
                    updateServerResult("Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: IOException) {
                updateServerResult("Network error: ${e.message}")
            } catch (e: Exception) {
                updateServerResult("Unexpected error: ${e.message}")
            } finally {
                updateJetsonIsWorking(false)
            }
        }
    }

    /** Streams the WAV bytes into Downloads and returns the URI. */
    private suspend fun saveWavToDownloads(body: ResponseBody): Uri =
        withContext(Dispatchers.IO) {
            val fileName = "generated_${System.currentTimeMillis()}.wav"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // MediaStore on Android 10+
                val resolver = context.contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/wav")
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
        // release any old player
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            setOnPreparedListener { mp -> mp.start() }
            setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
            }
            setOnErrorListener { mp, what, extra ->
                mp.release()
                mediaPlayer = null
                true
            }
            prepareAsync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
