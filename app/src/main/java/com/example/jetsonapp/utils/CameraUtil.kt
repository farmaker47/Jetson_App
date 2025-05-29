package com.example.jetsonapp.utils

import org.json.JSONObject
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
}
