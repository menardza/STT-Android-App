package com.sttapp.stt

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Android's native SpeechRecognizer implementation of STTEngine.
 * 
 * This wraps Android's built-in SpeechRecognizer API to work with the STTEngine interface.
 * It supports both online and offline recognition (offline requires language packs).
 */
class AndroidSpeechRecognizerEngine(private val context: Context) : STTEngine {
    companion object {
        private const val TAG = "AndroidSTTEngine"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: STTEngine.STTListener? = null
    
    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
    
    override fun initialize(listener: STTEngine.STTListener) {
        this.listener = listener
        if (speechRecognizer == null && isAvailable()) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.i(TAG, "Android SpeechRecognizer initialized")
        } else if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }
    
    override fun startListening(language: String?, preferOffline: Boolean) {
        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer not initialized")
            listener?.onError(-1, "SpeechRecognizer not initialized")
            return
        }
        
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language ?: Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        try {
            Log.i(TAG, "Starting Android SpeechRecognizer...")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            listener?.onError(-1, "Error starting recognition: ${e.message}")
        }
    }
    
    override fun stopListening() {
        Log.i(TAG, "Stopping Android SpeechRecognizer...")
        speechRecognizer?.stopListening()
    }
    
    override fun cancel() {
        Log.i(TAG, "Cancelling Android SpeechRecognizer...")
        speechRecognizer?.cancel()
    }
    
    override fun destroy() {
        Log.i(TAG, "Destroying Android SpeechRecognizer...")
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }
    
    override fun getName(): String = "Android Native"
    
    override fun supportsPartialResults(): Boolean = true
    
    override fun supportsOffline(): Boolean = true
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d(TAG, "Ready for speech")
                listener?.onReadyForSpeech()
            }
            
            override fun onBeginningOfSpeech() {
                Log.i(TAG, "Beginning of speech")
                listener?.onBeginningOfSpeech()
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                listener?.onRmsChanged(rmsdB)
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }
            
            override fun onEndOfSpeech() {
                Log.i(TAG, "End of speech")
                listener?.onEndOfSpeech()
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error: $error"
                }
                Log.e(TAG, "Recognition error: $errorMessage (code: $error)")
                listener?.onError(error, errorMessage)
            }
            
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.i(TAG, "Recognition results: ${matches[0]}")
                    listener?.onResults(matches)
                } else {
                    Log.w(TAG, "Recognition results empty")
                    listener?.onResults(emptyList())
                }
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d(TAG, "Partial results: ${matches[0]}")
                    listener?.onPartialResults(matches)
                }
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                Log.d(TAG, "Recognition event: $eventType")
            }
        }
    }
}

