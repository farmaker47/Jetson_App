package com.example.jetsonapp

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class JetsonViewModel @javax.inject.Inject constructor(application: Application) :
    AndroidViewModel(application) {

    var topText = mutableStateOf("Initial Top Text")

    var serverResult = mutableStateOf("Server result will appear here")
        private set

    fun sendData() {
        serverResult.value = "Data sent! Server responded successfully."
    }
}