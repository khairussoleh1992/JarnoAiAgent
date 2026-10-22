package com.example.jarnoai

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var llmManager: LlmManager
    private lateinit var txtStatus: TextView
    private lateinit var txtResponse: TextView
    private lateinit var btnCall: Button
    private lateinit var tts: TextToSpeech

    private var isCalling = false

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && isCalling) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            if (spokenText.isNotBlank()) {
                processAI(spokenText)
            } else {
                listenToUser() // Dengar lagi jika kosong
            }
        } else {
            endCall()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Layar tetap menyala selama aplikasi terbuka
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        txtStatus = findViewById(R.id.txtStatus)
        txtResponse = findViewById(R.id.txtResponse)
        btnCall = findViewById(R.id.btnCall)

        llmManager = LlmManager(applicationContext)
        tts = TextToSpeech(this, this)

        // Otomatis load model pertama yang ditemukan di memori
        autoLoadDefaultModel()

        btnCall.setOnClickListener {
            if (!isCalling) {
                startCall()
            } else {
                endCall()
            }
        }
    }

    private fun startCall() {
        isCalling = true
        btnCall.text = "AKHIRI PANGGILAN"
        btnCall.setBackgroundColor(android.graphics.Color.RED)
        txtStatus.text = "JARNO TERHUBUNG (MENDENGAR...)"

        // Jalankan Foreground Service agar tetap hidup di background
        val serviceIntent = Intent(this, JarnoForegroundService::class.java)
        startForegroundService(serviceIntent)

        listenToUser()
    }

    private fun endCall() {
        isCalling = false
        btnCall.text = "MULAI PANGGILAN"
        btnCall.setBackgroundColor(android.graphics.Color.parseColor("#00C853"))
        txtStatus.text = "PANGGILAN BERAKHIR"
        tts.stop()
        stopService(Intent(this, JarnoForegroundService::class.java))
    }

    private fun listenToUser() {
        if (!isCalling) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara pada Jarno...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Fitur suara error.", Toast.LENGTH_SHORT).show()
            endCall()
        }
    }

    private fun processAI(prompt: String) {
        txtStatus.text = "JARNO BERPIKIR..."
        txtResponse.text = "User: $prompt\n\nMemproses..."

        // Cek apakah user minta membaca layar atau kontrol HP
        val accessibility = JarnoAccessibilityService.instance
        var screenContext = ""
        if (prompt.lowercase().contains("baca layar") && accessibility != null) {
            screenContext = "\n[Isi Layar HP Saat Ini:\n${accessibility.getScreenText()}]"
        }

        val finalPrompt = "$prompt $screenContext"

        lifecycleScope.launch(Dispatchers.IO) {
            val response = llmManager.generateResponse(finalPrompt)
            withContext(Dispatchers.Main) {
                txtResponse.text = "User: $prompt\n\nJarno: $response"
                txtStatus.text = "JARNO BERBICARA..."
                
                // Jarvis berbicara
                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")

                // Setelah selesai bicara, lanjut mendengarkan lagi (Looping Call)
                if (isCalling) {
                    // Tunggu sebentar atau langsung dengar lagi setelah TTS selesai
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(3000) // Jeda singkat
                        if (isCalling) listenToUser()
                    }
                }
            }
        }
    }

        private lateinit var jarvisCore: View
    private var pulseAnimator: android.animation.AnimatorSet? = null

    // Di dalam onCreate(), tambahkan:
    // jarvisCore = findViewById(R.id.jarvisCore)

    private fun startJarvisAnimation() {
        val scaleX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f)
        val scaleY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f)
        val alpha = android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 1f)

        pulseAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(jarvisCore, scaleX, scaleY, alpha).apply {
            duration = 800
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopJarvisAnimation() {
        pulseAnimator?.cancel()
        jarvisCore.scaleX = 1f
        jarvisCore.scaleY = 1f
        jarvisCore.alpha = 1f
    }
    
    private fun autoLoadDefaultModel() {
        val modelFiles = filesDir.listFiles { file -> file.extension == "bin" || file.extension == "task" }?.map { it.name } ?: emptyList()
        if (modelFiles.isNotEmpty()) {
            val modelPath = File(filesDir, modelFiles[0]).absolutePath
            lifecycleScope.launch(Dispatchers.IO) {
                llmManager.loadModel(modelPath)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("id", "ID")
        }
    }

    override fun onDestroy() {
        endCall()
        super.onDestroy()
    }
}
