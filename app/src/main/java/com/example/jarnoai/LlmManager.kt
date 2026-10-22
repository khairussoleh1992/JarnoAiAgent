package com.example.jarnoai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class LlmManager(private val context: Context) {
    private var llmInference: LlmInference? = null

    fun loadModel(modelPath: String): String {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(150) // Dibatasi supaya tidak terlalu lama di CPU HP
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            "SUKSES"
        } catch (e: Exception) {
            e.printStackTrace()
            "Gagal memuat: ${e.localizedMessage ?: e.toString()}"
        }
    }

    fun generateResponse(prompt: String): String {
        return try {
            // Format template khusus Gemma supaya AI langsung paham konteks tanya-jawab
            val formattedPrompt = "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            
            llmInference?.generateResponse(formattedPrompt) ?: "Model AI belum dimuat."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
