package com.whispercppdemo.ui.main

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    var isStreaming by mutableStateOf(false)
        private set
    var models by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedModel by mutableStateOf("")
        private set

    private var recorder: Recorder = Recorder()
    private var whisperContext: com.whispercpp.whisper.WhisperContext? = null
    private var recordedFile: File? = null
    private var streamingJob: Job? = null
    private val streamingBuffer = mutableListOf<Short>()

    init {
        viewModelScope.launch {
            printSystemInfo()
            refreshModels()
        }
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", com.whispercpp.whisper.WhisperContext.getSystemInfo()))
    }

    private fun refreshModels() {
        models = application.assets.list("models/")?.toList() ?: emptyList()
        if (models.isNotEmpty()) {
            selectedModel = models[0]
        }
    }

    fun onModelSelected(model: String) {
        selectedModel = model
        viewModelScope.launch {
            loadModel(model)
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    private suspend fun loadModel(modelName: String) = withContext(Dispatchers.IO) {
        canTranscribe = false
        printMessage("Loading model $modelName...\n")
        try {
            whisperContext?.release()
            whisperContext = com.whispercpp.whisper.WhisperContext.createContextFromAsset(application.assets, "models/" + modelName)
            printMessage("Loaded model $modelName.\n")
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        return@withContext decodeWaveFile(file)
    }

    private suspend fun transcribeAudio(file: File) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false

        try {
            printMessage("Reading wave samples... ")
            val data = readAudioSamples(file)
            printMessage("${data.size / (16000 / 1000)} ms\n")
            printMessage("Transcribing data...\n")
            val start = System.currentTimeMillis()
            val text = whisperContext?.transcribeData(data)
            val elapsed = System.currentTimeMillis() - start
            printMessage("Done ($elapsed ms): \n$text\n")
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }

        canTranscribe = true
    }

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
            } else {
                val file = getTempFileForRecording()
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            printMessage("${e.localizedMessage}\n")
                            isRecording = false
                        }
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    fun toggleStream() = viewModelScope.launch {
        try {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming()
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isStreaming = false
        }
    }

    private suspend fun startStreaming() {
        isStreaming = true
        streamingBuffer.clear()
        printMessage("Starting stream...\n")
        
        recorder.startStreaming(
            onBufferReceived = { chunk ->
                synchronized(streamingBuffer) {
                    streamingBuffer.addAll(chunk.toList())
                }
            },
            onError = { e ->
                viewModelScope.launch {
                    printMessage("${e.localizedMessage}\n")
                    isStreaming = false
                }
            }
        )

        streamingJob = viewModelScope.launch(Dispatchers.Default) {
            while (isStreaming) {
                val dataToTranscribe = synchronized(streamingBuffer) {
                    if (streamingBuffer.size >= 16000 * 2) { // 2 seconds of audio
                        val data = streamingBuffer.toShortArray()
                        streamingBuffer.clear() 
                        data
                    } else {
                        null
                    }
                }

                if (dataToTranscribe != null) {
                    val floatData = FloatArray(dataToTranscribe.size) { i -> dataToTranscribe[i] / 32768.0f }
                    val text = whisperContext?.transcribeData(floatData, false)
                    if (!text.isNullOrBlank()) {
                        printMessage("Stream: $text\n")
                    }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private suspend fun stopStreaming() {
        isStreaming = false
        recorder.stopRecording()
        streamingJob?.cancel()
        streamingJob = null
        printMessage("Stream stopped.\n")
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
}
