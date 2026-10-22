package com.example.jarnoai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LlmManager(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun loadModel(modelPath: String): Boolean {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.7f)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun generateResponse(prompt: String): String {
        return try {
            llmInference?.generateResponse(prompt) ?: "Model AI belum dimuat."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
