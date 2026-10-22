package com.example.jarnoai

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
    private lateinit var inputPrompt: EditText
    private lateinit var btnSend: Button
    private lateinit var btnMic: Button
    private lateinit var btnBrowseModel: Button
    private lateinit var txtResponse: TextView
    private lateinit var spinnerModels: Spinner
    private lateinit var tts: TextToSpeech

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            inputPrompt.setText(spokenText)
            if (spokenText.isNotBlank()) {
                processAI(spokenText)
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveFileToInternalStorage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputPrompt = findViewById(R.id.inputPrompt)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        btnBrowseModel = findViewById(R.id.btnBrowseModel)
        txtResponse = findViewById(R.id.txtResponse)
        spinnerModels = findViewById(R.id.spinnerModels)

        llmManager = LlmManager(applicationContext)
        tts = TextToSpeech(this, this)
        refreshModelList()

        btnBrowseModel.setOnClickListener { filePickerLauncher.launch(arrayOf("*/*")) }

        btnMic.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara pada Jarno...")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Fitur suara tidak didukung.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSend.setOnClickListener {
            val prompt = inputPrompt.text.toString()
            if (prompt.isNotBlank()) {
                processAI(prompt)
            }
        }
    }

    private fun processAI(prompt: String) {
        // Contoh jika ingin Jarno membaca teks layar secara otomatis saat diperintah
        val screenContent = if (prompt.lowercase().contains("baca layar")) {
            JarnoAccessibilityService.instance?.getScreenText() ?: "Layanan aksesibilitas belum aktif."
        } else {
            ""
        }

        val finalPrompt = if (screenContent.isNotBlank()) "Konteks Layar:\n$screenContent\n\nPerintah: $prompt" else prompt

        txtResponse.append("\n\nUser: $prompt\nJarno: Memproses...")
        lifecycleScope.launch(Dispatchers.IO) {
            val response = llmManager.generateResponse(finalPrompt)
            withContext(Dispatchers.Main) {
                txtResponse.text = response
                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    private fun saveFileToInternalStorage(uri: Uri) {
        val fileName = getFileName(uri) ?: "model.bin"
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
                result = result?.substring(cut + 1) // <-- Perbaikan di sini (sebelumnya cut!)
            }
        }
        return result
    }

    private fun refreshModelList() {
        val modelFiles = filesDir.listFiles { file -> file.extension == "bin" }?.map { it.name } ?: emptyList()

        if (modelFiles.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelFiles)
            spinnerModels.adapter = adapter
            loadSelectedModel(modelFiles[0])

            spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    loadSelectedModel(modelFiles[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        } else {
            txtResponse.text = "Jarno: Pilih file model .bin terlebih dahulu."
        }
    }

        private fun loadSelectedModel(fileName: String) {
        val modelPath = File(filesDir, fileName).absolutePath
        txtResponse.text = "Memuat model $fileName..."
        lifecycleScope.launch(Dispatchers.IO) {
            val resultMessage = llmManager.loadModel(modelPath)
            withContext(Dispatchers.Main) {
                if (resultMessage == "SUKSES") {
                    txtResponse.text = "Jarno online. Model $fileName siap digunakan."
                } else {
                    txtResponse.text = resultMessage // Menampilkan pesan error spesifik
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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
