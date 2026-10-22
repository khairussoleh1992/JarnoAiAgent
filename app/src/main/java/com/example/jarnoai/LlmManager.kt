package com.example.jarnoai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LlmManager(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun loadModel(modelPath: String): String {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .build() // setTemperature dihapus karena tidak didukung di versi ini
            
            llmInference = LlmInference.createFromOptions(context, options)
            "SUKSES"
        } catch (e: Exception) {
            e.printStackTrace()
            "Gagal memuat: ${e.localizedMessage ?: e.toString()}"
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
