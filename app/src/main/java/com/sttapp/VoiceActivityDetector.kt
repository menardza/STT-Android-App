package com.sttapp

import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.sqrt

/**
 * Voice Activity Detector (VAD) - similar to myAssistant's VAD implementation.
 * 
 * This class monitors audio input to detect when speech starts and ends based on
 * RMS (Root Mean Square) audio level analysis. It's used for automatic speech
 * detection without requiring manual button presses.
 * 
 * How it works:
 * 1. Continuously captures audio from the microphone
 * 2. Calculates RMS (Root Mean Square) for each audio buffer
 * 3. Compares RMS to a threshold to determine if speech is present
 * 4. Detects speech start when RMS exceeds threshold
 * 5. Detects speech end when RMS falls below threshold for a sustained period
 * 6. Maintains a pre-speech buffer to capture audio before speech is detected
 * 
 * This implementation mirrors myAssistant's approach:
 * - RMS-based silence detection
 * - Configurable silence threshold and duration
 * - Pre-speech buffer to capture audio before detection
 * 
 * @param sampleRate Audio sample rate in Hz (default: 16000 - standard for speech)
 * @param silenceThreshold RMS value below which audio is considered silence (default: 200)
 * @param silenceDurationMs How long silence must persist to consider speech ended (default: 1500ms)
 * @param preSpeechBufferDurationMs How much audio to buffer before speech starts (default: 500ms)
 */
class VoiceActivityDetector(
    private val sampleRate: Int = 16000,  // 16kHz is standard for speech recognition
    private val silenceThreshold: Int = 200,  // RMS threshold for silence detection
    private val silenceDurationMs: Long = 1500,  // Duration of silence to consider speech complete
    private val preSpeechBufferDurationMs: Long = 500  // Audio to keep before speech starts
) {
    companion object {
        // Log tag for filtering VAD logs
        private const val TAG = "VAD"
        
        // Audio configuration constants
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO  // Mono audio (single channel)
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT  // 16-bit PCM encoding
        private const val BUFFER_SIZE_MULTIPLIER = 2  // Use 2x minimum buffer size for smoother operation
    }

    // AudioRecord instance for capturing microphone input
    private var audioRecord: AudioRecord? = null
    
    // Flag indicating if VAD is currently monitoring audio
    private var isDetecting = false
    
    // Flag indicating if speech is currently being detected
    private var isSpeaking = false
    
    // Timestamp when silence period started (not currently used but available for future use)
    private var silenceStartTime: Long = 0
    
    // Timestamp of last detected speech activity
    private var lastSpeechTime: Long = 0
    
    // Buffer to store audio frames before speech is detected
    // This allows capturing the beginning of speech that might start before detection
    private val preSpeechBuffer = mutableListOf<ShortArray>()
    
    // Buffer to store audio frames during detected speech
    // These are combined and passed to onSpeechEnd callback
    private val speechFrames = mutableListOf<ShortArray>()
    
    // Calculate buffer size: minimum required size multiplied by multiplier for smoother operation
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER

    /**
     * Listener interface for VAD events.
     * 
     * Implement this to receive callbacks when:
     * - Speech starts (audio level exceeds threshold)
     * - Speech ends (silence detected for configured duration)
     * - Silence periods occur (no speech detected)
     */
    interface VADListener {
        /**
         * Called when speech is detected (audio level exceeds threshold).
         * This is the signal to start recording/processing speech.
         */
        fun onSpeechStart()
        
        /**
         * Called when speech ends (silence detected for configured duration).
         * 
         * @param audioData Complete audio data captured during the speech segment,
         *                  including pre-speech buffer and all speech frames
         */
        fun onSpeechEnd(audioData: ShortArray)
        
        /**
         * Called during silent periods when no speech is detected.
         * Useful for UI updates or other silent-period handling.
         */
        fun onSilence()
    }

    // Current listener instance (null if not set)
    private var listener: VADListener? = null

    /**
     * Sets the listener to receive VAD events.
     * 
     * @param listener The VADListener implementation to receive callbacks
     */
    fun setListener(listener: VADListener) {
        this.listener = listener
    }

    /**
     * Starts voice activity detection.
     * 
     * This method:
     * 1. Creates and initializes an AudioRecord instance
     * 2. Starts recording from the microphone
     * 3. Launches a background thread to continuously monitor audio levels
     * 4. Resets internal state (buffers, flags)
     * 
     * The detection runs in a separate thread to avoid blocking the UI thread.
     * Requires RECORD_AUDIO permission to be granted.
     * 
     * @throws SecurityException if RECORD_AUDIO permission is not granted
     */
    fun startDetection() {
        // Prevent starting if already detecting
        if (isDetecting) {
            Log.w(TAG, "VAD already detecting")
            return
        }

        try {
            // Create AudioRecord instance to capture audio from microphone
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,  // Use device microphone
                sampleRate,  // 16kHz sample rate
                CHANNEL_CONFIG,  // Mono channel
                AUDIO_FORMAT,  // 16-bit PCM
                bufferSize  // Buffer size for audio capture
            )

            // Verify AudioRecord initialized successfully
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            // Start recording audio
            audioRecord?.startRecording()
            
            // Update state flags
            isDetecting = true
            isSpeaking = false
            
            // Clear buffers for fresh start
            preSpeechBuffer.clear()
            speechFrames.clear()
            
            Log.i(TAG, "VAD: Started detection (threshold=$silenceThreshold, silence_duration=${silenceDurationMs}ms)")

            // Launch detection in background thread to avoid blocking UI
            Thread {
                detectVoiceActivity()
            }.start()
        } catch (e: Exception) {
            // Handle initialization errors (permission, hardware issues, etc.)
            Log.e(TAG, "Error starting VAD detection", e)
            isDetecting = false
        }
    }

    /**
     * Stops voice activity detection.
     * 
     * This method:
     * 1. Stops the detection loop
     * 2. Stops audio recording
     * 3. Releases AudioRecord resources
     * 4. Cleans up state
     * 
     * Should be called when VAD is no longer needed to free resources.
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
        
        Log.i(TAG, "VAD: Stopped detection")
    }

    /**
     * Main detection loop that runs in a background thread.
     * 
     * This method:
     * 1. Continuously reads audio buffers from the microphone
     * 2. Calculates RMS (Root Mean Square) for each buffer
     * 3. Compares RMS to threshold to detect speech vs silence
     * 4. Manages pre-speech buffering
     * 5. Tracks silence duration to detect speech end
     * 6. Calls listener callbacks when speech starts/ends
     * 
     * The loop continues until stopDetection() is called or an error occurs.
     */
    private fun detectVoiceActivity() {
        // Buffer to hold audio data read from microphone
        val buffer = ShortArray(bufferSize)
        
        // Calculate how many audio chunks represent the silence duration
        // This allows us to detect sustained silence periods
        val chunksForSilence = (silenceDurationMs * sampleRate / 1000) / bufferSize
        var silenceChunks = 0  // Counter for consecutive silent chunks

        // Main detection loop - runs until stopped
        while (isDetecting && audioRecord != null) {
            // Read audio data from microphone
            // Returns number of samples read, or negative value on error
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            // Skip if read failed or returned no data
            if (read <= 0) {
                continue
            }

            // Copy only the actual data read (buffer may be larger than data)
            val audioData = buffer.copyOf(read)
            
            // Calculate RMS (Root Mean Square) - measures audio energy/level
            val rms = calculateRMS(audioData)

            if (isSpeaking) {
                // Currently detecting speech - add this frame to speech buffer
                speechFrames.add(audioData)
                lastSpeechTime = System.currentTimeMillis()

                // Check if audio level dropped below threshold (silence detected)
                if (rms < silenceThreshold) {
                    silenceChunks++
                    // If silence persists for configured duration, speech has ended
                    if (silenceChunks > chunksForSilence) {
                        Log.i(TAG, "VAD: Silence detected after speech, processing...")
                        isSpeaking = false
                        // Combine all speech frames (including pre-speech buffer) into single array
                        val combinedAudio = combineAudioFrames(speechFrames)
                        // Notify listener that speech has ended with complete audio data
                        listener?.onSpeechEnd(combinedAudio)
                        // Clear buffers for next detection cycle
                        speechFrames.clear()
                        preSpeechBuffer.clear()
                        silenceChunks = 0
                    }
                } else {
                    // Audio level is still above threshold - reset silence counter
                    silenceChunks = 0
                }
            } else {
                // Not currently detecting speech - maintain pre-speech buffer and check for speech start
                
                // Maintain a rolling buffer of recent audio (pre-speech buffer)
                // This captures audio before speech is detected, so we don't lose the beginning
                if (preSpeechBuffer.size * bufferSize < (preSpeechBufferDurationMs * sampleRate / 1000)) {
                    // Buffer not full yet - add frame
                    preSpeechBuffer.add(audioData)
                } else {
                    // Buffer full - remove oldest frame and add new one (rolling buffer)
                    preSpeechBuffer.removeAt(0)
                    preSpeechBuffer.add(audioData)
                }

                // Check if audio level exceeds threshold (speech detected)
                if (rms > silenceThreshold) {
                    Log.i(TAG, "VAD: Speech detected (RMS=$rms)")
                    isSpeaking = true
                    // Notify listener that speech has started
                    listener?.onSpeechStart()
                    // Initialize speech frames with pre-speech buffer + current frame
                    // This ensures we capture the complete speech including the beginning
                    speechFrames.clear()
                    speechFrames.addAll(preSpeechBuffer)
                    speechFrames.add(audioData)
                    silenceChunks = 0
                } else {
                    // Still in silence - notify listener (optional, for UI updates)
                    listener?.onSilence()
                }
            }
        }
    }

    /**
     * Calculates the RMS (Root Mean Square) value for audio data.
     * 
     * RMS is a measure of audio energy/amplitude. Higher RMS values indicate louder audio.
     * This is used to distinguish between speech (higher RMS) and silence (lower RMS).
     * 
     * Formula: RMS = sqrt(sum(sample^2) / count)
     * 
     * @param audioData Array of audio samples (16-bit PCM)
     * @return RMS value as a Double (higher = louder audio)
     */
    private fun calculateRMS(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return 0.0
        
        // Sum of squares of all samples
        var sum = 0.0
        for (sample in audioData) {
            val value = sample.toDouble()
            sum += value * value  // Square each sample
        }
        // Return square root of mean of squares
        return sqrt(sum / audioData.size)
    }

    /**
     * Combines multiple audio frames into a single array.
     * 
     * This is used to combine the pre-speech buffer and all speech frames
     * into a complete audio segment that can be passed to the listener.
     * 
     * @param frames List of audio frame arrays to combine
     * @return Single array containing all frames concatenated together
     */
    private fun combineAudioFrames(frames: List<ShortArray>): ShortArray {
        // Calculate total size needed
        val totalSize = frames.sumOf { it.size }
        val combined = ShortArray(totalSize)
        var offset = 0
        
        // Copy each frame into the combined array
        for (frame in frames) {
            System.arraycopy(frame, 0, combined, offset, frame.size)
            offset += frame.size
        }
        return combined
    }

    /**
     * Returns whether VAD is currently detecting.
     * 
     * @return True if detection is active, false otherwise
     */
    fun isDetecting(): Boolean = isDetecting
}

