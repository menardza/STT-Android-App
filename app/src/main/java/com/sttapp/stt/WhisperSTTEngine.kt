package com.sttapp.stt

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Whisper STT Engine implementation using whisper.cpp for on-device processing.
 * 
 * This engine uses whisper.cpp (via JNI/NDK) for fully offline speech recognition.
 * The model file should be placed in app/src/main/assets/models/ or provided via modelPath.
 */
class WhisperSTTEngine(
    private val context: Context,
    private val modelPath: String? = null, // Path to model file, or null to use default from assets
    private val useLocalWhisperCpp: Boolean = true // Always use local whisper.cpp now
) : STTEngine {
    companion object {
        private const val TAG = "WhisperSTTEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
        private const val DEFAULT_MODEL_NAME = "ggml-base.bin" // Default model name in assets
        private const val MODELS_DIR = "models"
    }
    
    private var listener: STTEngine.STTListener? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Whisper.cpp native context
    private var whisperContext: Long = 0
    private var modelLoaded = false
    
    override fun isAvailable(): Boolean {
        // Check if native library can be loaded
        // We'll try to load it if not already loaded, but don't fail if it's not available yet
        return try {
            // Try to access the native library - if it loads, we're good
            // The library will be loaded when WhisperNative object is first accessed
            // We just check if the class can be instantiated without errors
            WhisperNative.javaClass
            Log.d(TAG, "Whisper native library class accessible")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Whisper native library not loaded yet (will load on first use): ${e.message}")
            // Still return true - the library might load later when actually used
            // This allows the engine to appear in the list even if library isn't loaded yet
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Whisper availability", e)
            false
        }
    }
    
    override fun initialize(listener: STTEngine.STTListener) {
        this.listener = listener
        
        if (useLocalWhisperCpp) {
            loadModel()
        } else {
            Log.w(TAG, "Server mode is deprecated. Use useLocalWhisperCpp=true for whisper.cpp")
        }
    }
    
    /**
     * Loads the Whisper model from assets or provided path.
     */
    private fun loadModel() {
        scope.launch {
            try {
                val modelFile = getModelFile()
                if (modelFile == null || !modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${modelFile?.absolutePath}")
                    listener?.onError(-1, "Model file not found. Please ensure model is in assets/models/")
                    return@launch
                }
                
                Log.i(TAG, "Loading Whisper model from: ${modelFile.absolutePath}")
                whisperContext = WhisperNative.initContext(modelFile.absolutePath)
                
                if (whisperContext == 0L) {
                    Log.e(TAG, "Failed to initialize Whisper context")
                    listener?.onError(-1, "Failed to load Whisper model")
                    return@launch
                }
                
                modelLoaded = WhisperNative.isModelLoaded(whisperContext)
                if (modelLoaded) {
                    Log.i(TAG, "Whisper model loaded successfully")
                } else {
                    Log.e(TAG, "Model loaded but verification failed")
                    listener?.onError(-1, "Model verification failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Whisper model", e)
                listener?.onError(-1, "Error loading model: ${e.message}")
            }
        }
    }
    
    /**
     * Gets the model file path, copying from assets if necessary.
     */
    private suspend fun getModelFile(): File? = withContext(Dispatchers.IO) {
        // If modelPath is provided, use it
        if (!modelPath.isNullOrEmpty()) {
            val file = File(modelPath)
            if (file.exists()) {
                return@withContext file
            }
            Log.w(TAG, "Provided model path does not exist: $modelPath")
        }
        
        // Try to load from assets
        val modelName = modelPath?.substringAfterLast("/") ?: DEFAULT_MODEL_NAME
        val assetsModelPath = "$MODELS_DIR/$modelName"
        
        try {
            // Copy model from assets to internal storage
            val internalDir = context.filesDir
            val modelsDir = File(internalDir, MODELS_DIR)
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            
            val modelFile = File(modelsDir, modelName)
            
            // Only copy if file doesn't exist or is outdated
            if (!modelFile.exists() || modelFile.length() == 0L) {
                Log.i(TAG, "Copying model from assets to: ${modelFile.absolutePath}")
                context.assets.open(assetsModelPath).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model copied successfully (${modelFile.length()} bytes)")
            } else {
                Log.i(TAG, "Using existing model file: ${modelFile.absolutePath}")
            }
            
            return@withContext modelFile
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing model from assets: $assetsModelPath", e)
            return@withContext null
        }
    }
    
    override fun startListening(language: String?, preferOffline: Boolean) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        if (!modelLoaded) {
            Log.e(TAG, "Model not loaded yet")
            listener?.onError(-1, "Model not loaded. Please wait for initialization.")
            return
        }
        
        startLocalWhisperCpp(language)
    }
    
    private fun startLocalWhisperCpp(language: String?) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                listener?.onError(-1, "Failed to initialize audio recording")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            listener?.onReadyForSpeech()
            listener?.onBeginningOfSpeech()
            
            Log.i(TAG, "Starting Whisper.cpp local recognition...")
            
            // Start recording in background coroutine
            recordingJob = scope.launch {
                val audioBuffer = mutableListOf<Short>()
                val buffer = ShortArray(bufferSize)
                
                while (isRecording && isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        audioBuffer.addAll(buffer.take(read))
                    }
                    
                    // Process audio in chunks or when recording stops
                    // For now, we'll process when recording stops
                }
                
                // When recording stops, process audio with whisper.cpp
                if (audioBuffer.isNotEmpty()) {
                    processAudioWithWhisper(audioBuffer.toShortArray(), language)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Whisper recognition", e)
            listener?.onError(-1, "Error starting recognition: ${e.message}")
            isRecording = false
        }
    }
    
    private suspend fun processAudioWithWhisper(audioData: ShortArray, language: String?) {
        withContext(Dispatchers.IO) {
            try {
                listener?.onEndOfSpeech()
                
                Log.i(TAG, "Processing ${audioData.size} audio samples with Whisper...")
                
                // Process audio with whisper.cpp
                val transcription = WhisperNative.processAudio(
                    whisperContext,
                    audioData,
                    SAMPLE_RATE
                )
                
                if (transcription.isNotEmpty()) {
                    Log.i(TAG, "Whisper transcription: $transcription")
                    listener?.onResults(listOf(transcription))
                } else {
                    Log.w(TAG, "Empty transcription from Whisper")
                    listener?.onResults(emptyList())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio with Whisper", e)
                listener?.onError(-1, "Processing error: ${e.message}")
            }
        }
    }
    
    override fun stopListening() {
        Log.i(TAG, "Stopping Whisper recognition...")
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        listener?.onEndOfSpeech()
        
        // Process any remaining audio
        recordingJob?.let { job ->
            scope.launch {
                job.join() // Wait for current processing to finish
            }
        }
    }
    
    override fun cancel() {
        Log.i(TAG, "Cancelling Whisper recognition...")
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
    }
    
    override fun destroy() {
        Log.i(TAG, "Destroying Whisper STT Engine...")
        isRecording = false
        recordingJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        // Free Whisper context
        if (whisperContext != 0L) {
            WhisperNative.freeContext(whisperContext)
            whisperContext = 0
            modelLoaded = false
        }
        
        scope.cancel()
        listener = null
    }
    
    override fun getName(): String = "Whisper (whisper.cpp)"
    
    override fun supportsPartialResults(): Boolean = false // Whisper doesn't support partial results
    
    override fun supportsOffline(): Boolean = true // Always offline with whisper.cpp
}
