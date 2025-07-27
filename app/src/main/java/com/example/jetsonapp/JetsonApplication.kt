package com.example.jetsonapp

import android.app.Application
import com.facebook.soloader.SoLoader
import dagger.hilt.android.HiltAndroidApp


@HiltAndroidApp
class JetsonApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SoLoader. This is the crucial step.
        // It tells the app how to find and load the native libraries.
        SoLoader.init(this, /* native exopackage */ false)
    }
}
