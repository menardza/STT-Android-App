package com.sttapp.wakeword

import android.util.Log

/**
 * Base interface for wake word detectors, similar to myAssistant's WakeWordDetector.
 * 
 * This interface provides a common API for wake word detection implementations.
 * It allows the app to easily swap between different detection implementations
 * (e.g., SimpleWakeWordDetector, TensorFlow Lite-based detector, Porcupine, etc.)
 * without changing the calling code.
 * 
 * The interface follows a similar design to myAssistant's wake word detection system,
 * making it easy to port implementations between projects.
 * 
 * Implementations should:
 * - Process audio data and return confidence scores for each wake word
 * - Support multiple wake words simultaneously
 * - Provide reset functionality to clear internal state
 * - Handle audio in 16-bit PCM format (standard for speech processing)
 */
interface WakeWordDetector {
    /**
     * Predicts wake word scores from audio data.
     * 
     * This is the core method that analyzes audio and determines the likelihood
     * that each configured wake word is present in the audio.
     * 
     * In a real implementation, this would:
     * - Preprocess audio (e.g., MFCC, spectrogram, normalization)
     * - Feed through a neural network model (TensorFlow Lite, ONNX, etc.)
     * - Return confidence scores for each wake word
     * 
     * @param audioData Audio data as ShortArray (16-bit PCM samples)
     * @return Map of wake word names to confidence scores (0.0 to 1.0)
     *         Higher scores indicate higher confidence that the wake word is present
     */
    fun predict(audioData: ShortArray): Map<String, Float>
    
    /**
     * Resets the detector's internal state.
     * 
     * This should clear any internal buffers, state machines, or model state.
     * Useful for:
     * - Preventing false positives from repeated detections
     * - Resetting after a wake word is detected
     * - Clearing state between detection sessions
     */
    fun reset()
    
    /**
     * Checks if any wake word is detected above the specified threshold.
     * 
     * This is a convenience method that calls predict() and checks if any
     * wake word score exceeds the threshold.
     * 
     * @param audioData Audio data as ShortArray (16-bit PCM samples)
     * @param threshold Detection threshold (default: 0.9)
     *                  Scores must exceed this value to be considered a detection
     * @return True if any wake word detected above threshold, False otherwise
     */
    fun detect(audioData: ShortArray, threshold: Float = 0.9f): Boolean {
        val predictions = predict(audioData)
        return predictions.values.any { it > threshold }
    }
    
    /**
     * Returns the detected wake word name and score if detected.
     * 
     * This is a convenience method that calls predict() and returns the first
     * wake word that exceeds the threshold, along with its score.
     * 
     * @param audioData Audio data as ShortArray (16-bit PCM samples)
     * @param threshold Detection threshold (default: 0.9)
     *                  Scores must exceed this value to be considered a detection
     * @return Pair of (wake_word_name, score) if detected, null otherwise
     *         If multiple wake words exceed threshold, returns the first one found
     */
    fun getDetectedWakeWord(audioData: ShortArray, threshold: Float = 0.9f): Pair<String, Float>? {
        val predictions = predict(audioData)
        // Return the first wake word that exceeds threshold
        for ((wakeWord, score) in predictions) {
            if (score > threshold) {
                return Pair(wakeWord, score)
            }
        }
        return null
    }
}

