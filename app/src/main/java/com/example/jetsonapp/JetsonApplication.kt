package com.example.jetsonapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class JetsonApplication : Application() {

    override fun onCreate() {
        super.onCreate()

    }
}