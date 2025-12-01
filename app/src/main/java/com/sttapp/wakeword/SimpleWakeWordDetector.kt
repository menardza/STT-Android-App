package com.sttapp.wakeword

import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Simple wake word detector using audio pattern matching.
 * 
 * This is a placeholder/demonstration implementation that uses simple energy-based
 * detection. For production use, this should be replaced with a proper ML-based solution.
 * 
 * Current Implementation:
 * - Uses energy-based detection (audio level analysis)
 * - Not a real wake word detector - just demonstrates the detection loop
 * - Returns placeholder scores based on audio energy
 * 
 * For production, you would replace this with:
 * 1. TensorFlow Lite with trained wakeword models (e.g., openwakeword models)
 * 2. Porcupine/Picovoice SDK (commercial wake word detection)
 * 3. ONNX Runtime with openwakeword ONNX models
 * 4. Custom neural network models trained for specific wake words
 * 
 * The interface (WakeWordDetector) is designed to be easily swappable with
 * a real ML-based implementation without changing the calling code.
 * 
 * @param wakeWords List of wake words to detect (e.g., ["hey jarvis", "alexa"])
 * @param threshold Confidence threshold (0.0 to 1.0) - must exceed this to trigger (default: 0.9)
 * @param sampleRate Audio sample rate in Hz (default: 16000 - standard for speech)
 */
class SimpleWakeWordDetector(
    private val wakeWords: List<String> = listOf("hey jarvis", "alexa"),
    private val threshold: Float = 0.9f,
    private val sampleRate: Int = 16000
) : WakeWordDetector {
    
    companion object {
        // Log tag for filtering wake word detection logs
        private const val TAG = "WakeWordDetector"
    }
    
    // Flag indicating if wake word detection is currently active
    private var isDetecting = false
    
    // AudioRecord instance for capturing microphone input
    private var audioRecord: AudioRecord? = null
    
    // Size of audio buffer for reading from microphone
    private var bufferSize = 0
    
    /**
     * Listener interface for wake word detection events.
     * 
     * Implement this to receive callbacks when wake words are detected.
     */
    interface WakeWordListener {
        /**
         * Called when a wake word is detected in the audio stream.
         * 
         * @param wakeWord The specific wake word that was detected
         * @param score The confidence score (0.0 to 1.0) indicating detection certainty
         */
        fun onWakeWordDetected(wakeWord: String, score: Float)
    }
    
    // Current listener instance (null if not set)
    private var listener: WakeWordListener? = null
    
    /**
     * Sets the listener to receive wake word detection events.
     * 
     * @param listener The WakeWordListener implementation to receive callbacks
     */
    fun setListener(listener: WakeWordListener) {
        this.listener = listener
    }
    
    /**
     * Predicts wake word scores from audio data.
     * 
     * This is a placeholder implementation that uses simple energy-based detection.
     * In production, this would use a machine learning model (TensorFlow Lite, ONNX, etc.)
     * to analyze the audio and return actual wake word detection scores.
     * 
     * Current implementation:
     * - Calculates audio energy (amplitude)
     * - Converts energy to a score (0.0 to 1.0)
     * - Returns scores for all configured wake words
     * 
     * Production implementation would:
     * - Feed audio through a neural network model
     * - Return actual confidence scores for each wake word
     * - Handle audio preprocessing (MFCC, spectrograms, etc.)
     * 
     * @param audioData Audio data as ShortArray (16-bit PCM samples)
     * @return Map of wake word names to confidence scores (0.0 to 1.0)
     */
    override fun predict(audioData: ShortArray): Map<String, Float> {
        // Placeholder implementation - returns energy-based scores for demonstration
        // In production, this would use a TensorFlow Lite model or similar
        val scores = mutableMapOf<String, Float>()
        for (wakeWord in wakeWords) {
            // Simple energy-based detection (placeholder)
            // Calculate audio energy and normalize to 0.0-1.0 range
            val energy = calculateEnergy(audioData)
            // Normalize energy (divide by 10000 and clamp to 0.0-1.0)
            // This is arbitrary - real ML models would return actual confidence scores
            val score = (energy / 10000.0f).coerceIn(0.0f, 1.0f)
            scores[wakeWord] = score
        }
        return scores
    }
    
    /**
     * Resets the detector's internal state.
     * 
     * This can be used to clear any internal buffers or state between detections.
     * Useful for preventing false positives from repeated detections of the same audio.
     */
    override fun reset() {
        // Reset internal state if needed
        // In a real implementation, this might clear audio buffers, reset model state, etc.
        Log.d(TAG, "Wake word detector reset")
    }
    
    /**
     * Starts wake word detection.
     * 
     * This method:
     * 1. Calculates appropriate audio buffer size
     * 2. Creates and initializes an AudioRecord instance
     * 3. Starts recording from the microphone
     * 4. Launches a background thread to continuously monitor audio for wake words
     * 
     * The detection runs in a separate thread to avoid blocking the UI thread.
     * Requires RECORD_AUDIO permission to be granted.
     * 
     * @throws SecurityException if RECORD_AUDIO permission is not granted
     */
    fun startDetection() {
        // Prevent starting if already detecting
        if (isDetecting) {
            Log.w(TAG, "Wake word detection already started")
            return
        }
        
        try {
            // Calculate minimum buffer size required for audio recording
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            // Validate buffer size
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Invalid buffer size: $minBufferSize")
                return
            }
            
            // Use 2x minimum buffer size for smoother operation
            bufferSize = minBufferSize * 2
            Log.d(TAG, "Buffer size: $bufferSize (min: $minBufferSize)")
            
            // Create AudioRecord instance to capture audio from microphone
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,  // Use device microphone
                sampleRate,  // 16kHz sample rate
                android.media.AudioFormat.CHANNEL_IN_MONO,  // Mono channel
                android.media.AudioFormat.ENCODING_PCM_16BIT,  // 16-bit PCM
                bufferSize  // Buffer size for audio capture
            )
            
            // Verify AudioRecord initialized successfully
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed - state: ${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return
            }
            
            // Start recording audio
            audioRecord?.startRecording()
            isDetecting = true
            
            // Log configuration for debugging
            Log.i(TAG, "Wake word detection started successfully")
            Log.i(TAG, "  - Wake words: ${wakeWords.joinToString()}")
            Log.i(TAG, "  - Threshold: $threshold")
            Log.i(TAG, "  - Sample rate: $sampleRate Hz")
            Log.i(TAG, "  - Buffer size: $bufferSize")
            
            // Launch detection in background thread to avoid blocking UI
            Thread {
                detectWakeWords()
            }.start()
        } catch (e: SecurityException) {
            // Permission not granted
            Log.e(TAG, "SecurityException: Microphone permission not granted", e)
            isDetecting = false
        } catch (e: Exception) {
            // Handle other initialization errors
            Log.e(TAG, "Error starting wake word detection", e)
            isDetecting = false
            audioRecord?.release()
            audioRecord = null
        }
    }
    
    /**
     * Stops wake word detection.
     * 
     * This method:
     * 1. Stops the detection loop
     * 2. Stops audio recording
     * 3. Releases AudioRecord resources
     * 4. Cleans up state
     * 
     * Should be called when wake word detection is no longer needed to free resources
     * and save battery.
     */
    fun stopDetection() {
        // Only stop if currently detecting
        if (!isDetecting) {
            return
        }
        
        // Stop detection loop
        isDetecting = false
        
        // Stop recording and release audio resources
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        Log.i(TAG, "Wake word detection stopped")
    }
    
    /**
     * Main detection loop that runs in a background thread.
     * 
     * This method:
     * 1. Continuously reads audio buffers from the microphone
     * 2. Processes each buffer through the wake word detector (predict method)
     * 3. Checks if any wake word score exceeds the threshold
     * 4. Calls listener callback when wake word is detected
     * 5. Handles errors gracefully with retry logic
     * 
     * The loop continues until stopDetection() is called or too many errors occur.
     * 
     * Note: Current implementation uses placeholder energy-based detection.
     * In production, this would feed audio through a real ML model.
     */
    private fun detectWakeWords() {
        // Buffer to hold audio data read from microphone
        val buffer = ShortArray(bufferSize)
        var readCount = 0  // Counter for processed audio chunks (for logging)
        var errorCount = 0  // Counter for consecutive errors
        
        Log.d(TAG, "Wake word detection thread started")
        
        // Main detection loop - runs until stopped
        while (isDetecting && audioRecord != null) {
            try {
                // Read audio data from microphone
                // Returns number of samples read, or negative value on error
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                // Handle read errors
                if (read < 0) {
                    errorCount++
                    when (read) {
                        AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "ERROR_INVALID_OPERATION - AudioRecord not initialized properly")
                            break
                        }
                        AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "ERROR_BAD_VALUE - Invalid buffer parameters")
                            break
                        }
                        else -> {
                            Log.w(TAG, "AudioRecord.read() returned error: $read")
                        }
                    }
                    // Stop if too many errors (likely hardware/permission issue)
                    if (errorCount > 10) {
                        Log.e(TAG, "Too many read errors, stopping detection")
                        break
                    }
                    Thread.sleep(10) // Small delay before retry
                    continue
                }
                
                // Skip if no data read (shouldn't happen often)
                if (read == 0) {
                    Thread.sleep(10)
                    continue
                }
                
                readCount++
                // Log progress periodically (every 100 chunks)
                if (readCount % 100 == 0) {
                    Log.d(TAG, "Processing audio chunks... (count: $readCount)")
                }
                
                // Copy only the actual data read (buffer may be larger than data)
                val audioData = buffer.copyOf(read)
                
                // Calculate audio energy for debugging/logging
                val energy = calculateEnergy(audioData)
                
                // Log energy levels periodically for debugging (every 200 chunks)
                if (readCount % 200 == 0) {
                    Log.d(TAG, "Audio energy level: $energy (threshold would be: ${threshold * 10000})")
                }
                
                // TEST MODE: For debugging, log when loud audio is detected
                // This helps verify the detection loop is working correctly
                // In production, remove this and use actual ML model predictions
                if (energy > 5000 && readCount % 500 == 0) {
                    Log.w(TAG, "TEST MODE: High energy detected ($energy) - detection loop is working!")
                    Log.w(TAG, "NOTE: This is a placeholder. Real wake word detection requires ML models.")
                }
                
                // Check if any wake word is detected (using threshold)
                // This calls predict() which in production would use an ML model
                val result = getDetectedWakeWord(audioData, threshold)
                
                // If wake word detected, notify listener
                if (result != null) {
                    val (wakeWord, score) = result
                    Log.i(TAG, "Wake word detected: '$wakeWord' (score: $score)")
                    // Notify listener on main thread (caller should handle thread switching)
                    listener?.onWakeWordDetected(wakeWord, score)
                    // Reset detector state to avoid multiple detections from same audio
                    reset()
                }
            } catch (e: Exception) {
                // Handle exceptions in detection loop
                Log.e(TAG, "Error in detection loop", e)
                errorCount++
                // Stop if too many errors
                if (errorCount > 10) {
                    Log.e(TAG, "Too many errors in detection loop, stopping")
                    break
                }
                Thread.sleep(10) // Small delay before retry
            }
        }
        
        Log.i(TAG, "Wake word detection thread ended (processed $readCount chunks)")
        isDetecting = false
    }
    
    /**
     * Calculates the energy (amplitude) of audio data.
     * 
     * This is similar to RMS calculation - measures the average audio level.
     * Used for debugging and placeholder detection in the current implementation.
     * 
     * Formula: sqrt(sum(abs(sample)^2) / count)
     * 
     * @param audioData Array of audio samples (16-bit PCM)
     * @return Energy value as a Float (higher = louder audio)
     */
    private fun calculateEnergy(audioData: ShortArray): Float {
        if (audioData.isEmpty()) return 0.0f
        
        // Sum of squares of absolute sample values
        var sum = 0.0
        for (sample in audioData) {
            sum += abs(sample.toDouble()) * abs(sample.toDouble())
        }
        // Return square root of mean of squares
        return sqrt(sum / audioData.size).toFloat()
    }
    
    /**
     * Returns whether wake word detection is currently active.
     * 
     * @return True if detection is active, false otherwise
     */
    fun isDetecting(): Boolean = isDetecting
}

