package com.sttapp.stt

/**
 * Interface for Speech-to-Text engines.
 * 
 * This abstraction allows the app to support multiple STT engines:
 * - Android's native SpeechRecognizer
 * - Whisper (via whisper.cpp or faster-whisper server)
 * - Future engines can be easily added
 */
interface STTEngine {
    /**
     * Listener for STT recognition events.
     */
    interface STTListener {
        /**
         * Called when recognition is ready to receive speech input.
         */
        fun onReadyForSpeech()
        
        /**
         * Called when speech input has started.
         */
        fun onBeginningOfSpeech()
        
        /**
         * Called periodically with audio level (RMS in dB).
         * @param rmsdB Root Mean Square audio level in decibels
         */
        fun onRmsChanged(rmsdB: Float)
        
        /**
         * Called when speech input has ended.
         */
        fun onEndOfSpeech()
        
        /**
         * Called when final recognition results are available.
         * @param results List of transcription results, ordered by confidence (first is most likely)
         */
        fun onResults(results: List<String>)
        
        /**
         * Called with partial/intermediate recognition results (real-time).
         * @param partialResults List of partial transcription results
         */
        fun onPartialResults(partialResults: List<String>)
        
        /**
         * Called when an error occurs during recognition.
         * @param error Error code or message
         */
        fun onError(error: Int, message: String)
    }
    
    /**
     * Checks if this STT engine is available on the device.
     * @return true if the engine can be used, false otherwise
     */
    fun isAvailable(): Boolean
    
    /**
     * Initializes the STT engine.
     * Must be called before startListening().
     * @param listener The listener to receive recognition events
     */
    fun initialize(listener: STTListener)
    
    /**
     * Starts listening for speech input.
     * @param language Language code (e.g., "en-US") or null for default
     * @param preferOffline Whether to prefer offline recognition if available
     */
    fun startListening(language: String? = null, preferOffline: Boolean = true)
    
    /**
     * Stops listening for speech input.
     * Any speech already captured will still be processed.
     */
    fun stopListening()
    
    /**
     * Cancels the current recognition session.
     * Discards any captured audio without processing.
     */
    fun cancel()
    
    /**
     * Destroys the STT engine and releases all resources.
     * Must be called when the engine is no longer needed.
     */
    fun destroy()
    
    /**
     * Returns the name of this STT engine (for UI display).
     */
    fun getName(): String
    
    /**
     * Returns whether this engine supports partial results (real-time transcription).
     */
    fun supportsPartialResults(): Boolean
    
    /**
     * Returns whether this engine supports offline recognition.
     */
    fun supportsOffline(): Boolean
}

