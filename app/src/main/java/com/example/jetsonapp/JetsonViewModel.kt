package com.example.jetsonapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jetsonapp.internet.ApiService
import com.example.jetsonapp.internet.GenerateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

@HiltViewModel
class JetsonViewModel @javax.inject.Inject constructor(
    application: Application,
    private val apiService: ApiService
) :
    AndroidViewModel(application) {

    private val _userPrompt = MutableStateFlow("")
    private val userPrompt = _userPrompt.asStateFlow()
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

    fun sendData() {
        updateJetsonIsWorking(true)
        viewModelScope.launch {
            try {
                val request = GenerateRequest(
                    model = MODEL,
                    prompt = userPrompt.value,
                    stream = false
                )
                val response = apiService.generate(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        updateServerResult(body.response)
                        updateJetsonIsWorking(false)
                        Log.v("response_", body.response)
                    } else {
                        updateJetsonIsWorking(false)
                        updateServerResult("No response body received.")
                    }
                } else {
                    updateJetsonIsWorking(false)
                    updateServerResult("Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: IOException) {
                updateJetsonIsWorking(false)
                updateServerResult("Network error: ${e.message}")
            } catch (e: Exception) {
                updateJetsonIsWorking(false)
                updateServerResult("Unexpected error: ${e.message}")
            }
        }
    }

    companion object {
        private const val MODEL = "gemma3:1b"
    }
}
