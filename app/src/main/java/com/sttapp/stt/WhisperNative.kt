package com.sttapp.stt

import android.util.Log

/**
 * JNI wrapper for whisper.cpp native library.
 * 
 * This class provides Kotlin bindings to the native whisper.cpp functions
 * implemented in C++ via JNI.
 */
object WhisperNative {
    private const val TAG = "WhisperNative"
    
    init {
        try {
            System.loadLibrary("whisper-jni")
            Log.i(TAG, "Whisper native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load whisper native library", e)
        }
    }
    
    /**
     * Initialize Whisper context with a model file.
     * 
     * @param modelPath Path to the Whisper model file (GGML format)
     * @return Context pointer (0 if failed)
     */
    external fun initContext(modelPath: String): Long
    
    /**
     * Process audio data and return transcription.
     * 
     * @param contextPtr Context pointer from initContext()
     * @param audioData Audio samples (16-bit PCM)
     * @param sampleRate Sample rate in Hz (typically 16000)
     * @return Transcribed text
     */
    external fun processAudio(contextPtr: Long, audioData: ShortArray, sampleRate: Int): String
    
    /**
     * Free Whisper context and release resources.
     * 
     * @param contextPtr Context pointer from initContext()
     */
    external fun freeContext(contextPtr: Long)
    
    /**
     * Check if model is loaded.
     * 
     * @param contextPtr Context pointer from initContext()
     * @return true if model is loaded, false otherwise
     */
    external fun isModelLoaded(contextPtr: Long): Boolean
}

