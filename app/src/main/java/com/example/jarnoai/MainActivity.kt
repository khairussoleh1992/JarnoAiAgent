package com.example.jarnoai

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
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
    private lateinit var btnBrowseModel: Button
    private lateinit var spinnerModels: Spinner
    private lateinit var jarvisCore: View
    private lateinit var tts: TextToSpeech

    private var isCalling = false
    private var pulseAnimator: ObjectAnimator? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveFileToInternalStorage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        txtStatus = findViewById(R.id.txtStatus)
        txtResponse = findViewById(R.id.txtResponse)
        btnCall = findViewById(R.id.btnCall)
        btnBrowseModel = findViewById(R.id.btnBrowseModel)
        spinnerModels = findViewById(R.id.spinnerModels)
        jarvisCore = findViewById(R.id.jarvisCore)

        llmManager = LlmManager(applicationContext)
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()
        refreshModelList()

        btnBrowseModel.setOnClickListener { filePickerLauncher.launch(arrayOf("*/*")) }

        btnCall.setOnClickListener {
            if (!isCalling) {
                startCall()
            } else {
                endCall()
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                txtStatus.text = "STATUS: MENDENGAR..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                txtStatus.text = "STATUS: MEMPROSES..."
            }
            override fun onError(error: Int) {
                if (isCalling) {
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(1000)
                        listenToUser()
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    if (spokenText.isNotBlank()) {
                        processAI(spokenText)
                    } else if (isCalling) {
                        listenToUser()
                    }
                } else if (isCalling) {
                    listenToUser()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startCall() {
        isCalling = true
        btnCall.text = "AKHIRI PANGGILAN"
        btnCall.setBackgroundColor(android.graphics.Color.RED)

        startJarvisAnimation()

        val serviceIntent = Intent(this, JarnoForegroundService::class.java)
        startForegroundService(serviceIntent)

        listenToUser()
    }

    private fun endCall() {
        isCalling = false
        btnCall.text = "MULAI PANGGILAN"
        btnCall.setBackgroundColor(android.graphics.Color.parseColor("#00C853"))
        txtStatus.text = "STATUS: PANGGILAN BERAKHIR"

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) { }

        stopJarvisAnimation()
        tts.stop()
        stopService(Intent(this, JarnoForegroundService::class.java))
    }

    private fun startJarvisAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 1f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(jarvisCore, scaleX, scaleY, alpha).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopJarvisAnimation() {
        pulseAnimator?.cancel()
        jarvisCore.scaleX = 1f
        jarvisCore.scaleY = 1f
        jarvisCore.alpha = 1f
    }

    private fun listenToUser() {
        if (!isCalling) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            // Aman dari error
        }
    }

    private fun processAI(prompt: String) {
        txtStatus.text = "STATUS: JARNO BERPIKIR..."
        txtResponse.text = "User: $prompt\n\nMemproses..."

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
                txtStatus.text = "STATUS: JARNO BERBICARA..."
                
                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "TTS_ID")

                if (isCalling) {
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(4000)
                        if (isCalling) listenToUser()
                    }
                }
            }
        }
    }

    private fun saveFileToInternalStorage(uri: Uri) {
        val fileName = getFileName(uri) ?: "model.task"
        val destinationFile = File(filesDir, fileName)

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(this, "Model disimpan: $fileName", Toast.LENGTH_SHORT).show()
            refreshModelList()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyalin: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun refreshModelList() {
        val modelFiles = filesDir.listFiles { file -> file.extension == "bin" || file.extension == "task" }?.map { it.name } ?: emptyList()

        if (modelFiles.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelFiles)
            spinnerModels.adapter = adapter
            loadSelectedModel(modelFiles[0])

            spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    loadSelectedModel(modelFiles[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        } else {
            txtResponse.text = "Jarno: Belum ada file model .task/.bin. Silakan klik tombol 'AMBIL FILE MODEL BARU' di atas."
        }
    }

    private fun loadSelectedModel(fileName: String) {
        val modelPath = File(filesDir, fileName).absolutePath
        txtStatus.text = "STATUS: MEMUAT $fileName..."
        lifecycleScope.launch(Dispatchers.IO) {
            val result = llmManager.loadModel(modelPath)
            withContext(Dispatchers.Main) {
                if (result == "SUKSES") {
                    txtStatus.text = "STATUS: SIAP"
                    txtResponse.text = "Model $fileName berhasil dimuat. Siap melakukan panggilan."
                } else {
                    txtStatus.text = "STATUS: GAGAL"
                    txtResponse.text = result
                }
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
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
