package com.example.jetsonapp.utils

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

object CameraUtil {
    fun extractFunctionName(response: String): String? {
        // Regular expression to match the JSON code block
        val pattern = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL)
        val matcher = pattern.matcher(response)

        return if (matcher.find()) {
            val jsonString = matcher.group(1)
            try {
                val jsonObject = JSONObject(jsonString)
                when {
                    jsonObject.has("function_name") -> jsonObject.getString("function_name")
                    jsonObject.has("functionName") -> jsonObject.getString("functionName")
                    jsonObject.has("name") -> jsonObject.getString("name")
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun Context.createTempPictureUri(
        fileName: String = "picture_${System.currentTimeMillis()}", fileExtension: String = ".png"
    ): Uri {
        val tempFile = File.createTempFile(
            fileName, fileExtension, cacheDir
        ).apply {
            createNewFile()
        }

        return FileProvider.getUriForFile(
            applicationContext,
            "com.example.jetsonapp.provider" /* {applicationId}.provider */,
            tempFile
        )
    }

    fun checkFrontCamera(context: Context, callback: (Boolean) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            try {
                // Attempt to select the default front camera
                val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                callback(hasFront)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
