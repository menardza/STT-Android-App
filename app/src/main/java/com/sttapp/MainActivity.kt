package com.sttapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sttapp.databinding.ActivityMainBinding
import com.sttapp.stt.AndroidSpeechRecognizerEngine
import com.sttapp.stt.STTEngine
import com.sttapp.stt.WhisperSTTEngine
import com.sttapp.wakeword.SimpleWakeWordDetector
import java.util.Locale

/**
 * MainActivity - The primary activity for the Android Speech-to-Text (STT) application.
 * 
 * This activity provides a comprehensive speech recognition interface with the following features:
 * 1. Manual recording: Start/stop recording via button press
 * 2. Auto-record mode: Automatically starts recording when VAD detects speech
 * 3. Wake word detection: Automatically starts recording when wake words are detected
 * 4. ADB command support: Can be controlled remotely via ADB broadcast commands
 * 5. Real-time transcription: Shows partial results as speech is being processed
 * 6. Multiple STT engines: Supports Android Native and Whisper (faster-whisper)
 * 
 * The app supports multiple STT engines:
 * - Android's native SpeechRecognizer API (online/offline with language packs)
 * - Whisper (via faster-whisper server or whisper.cpp)
 */
class MainActivity : AppCompatActivity() {
    companion object {
        // Log tag for filtering logs in logcat
        private const val TAG = "STTApp"
        
        // Broadcast action strings for ADB remote control
        // These allow external tools to start/stop recording via ADB commands
        const val ACTION_START_RECORDING = "com.sttapp.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.sttapp.ACTION_STOP_RECORDING"
    }

    // View binding instance - provides type-safe access to UI elements defined in activity_main.xml
    private lateinit var binding: ActivityMainBinding
    
    // STT Engine instance - handles the actual speech recognition
    // Nullable because it's initialized after permission is granted and engine is selected
    private var sttEngine: STTEngine? = null
    
    // Available STT engines
    private val availableEngines = mutableListOf<STTEngine>()
    
    // Flag to track if we're currently listening for speech
    // Used to prevent multiple simultaneous recognition sessions
    private var isListening = false
    
    // Voice Activity Detector - monitors audio levels to detect when speech starts/ends
    // Used for auto-record mode to automatically start recording when speech is detected
    private var vad: VoiceActivityDetector? = null
    
    // Flag indicating if auto-record mode is enabled
    // When true, VAD will automatically start recording when speech is detected
    private var autoRecordMode = false
    
    // Wake word detector instance - continuously monitors audio for wake words like "hey jarvis"
    // When a wake word is detected, recording automatically starts
    private var wakeWordDetector: SimpleWakeWordDetector? = null
    
    // Flag indicating if wake word detection mode is enabled
    private var wakeWordMode = false

    /**
     * Permission launcher for requesting RECORD_AUDIO permission.
     * 
     * This uses the modern Activity Result API (replacing deprecated onRequestPermissionsResult).
     * When the user grants or denies microphone permission, this callback is invoked.
     * 
     * If granted:
     * - Initializes the selected STT engine so recording can begin
     * - If wake word mode was previously enabled, starts wake word detection now
     * 
     * If denied:
     * - Shows a toast message explaining why permission is needed
     * - Disables the wake word switch in the UI to reflect the permission state
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted - initialize selected STT engine
            initializeSTTEngine()
            // If wake word mode was enabled before permission was granted, start it now
            // This handles the case where user enables wake word switch before granting permission
            if (wakeWordMode) {
                wakeWordDetector?.startDetection()
                Log.i(TAG, "Wake word detection started after permission granted")
            }
        } else {
            // Permission denied - inform user and update UI
            Toast.makeText(
                this,
                "Microphone permission is required for speech recognition",
                Toast.LENGTH_LONG
            ).show()
            // Disable wake word switch if permission denied to keep UI consistent
            binding.wakeWordSwitch.isChecked = false
        }
    }

    /**
     * BroadcastReceiver for handling ADB remote control commands.
     * 
     * This receiver listens for broadcast intents that can be sent via ADB commands.
     * This allows external tools, scripts, or other apps to remotely control recording.
     * 
     * Supported commands:
     * - ACTION_START_RECORDING: Starts speech recognition if not already listening
     * - ACTION_STOP_RECORDING: Stops speech recognition if currently listening
     * 
     * Example ADB usage:
     *   adb shell am broadcast -a com.sttapp.ACTION_START_RECORDING
     *   adb shell am broadcast -a com.sttapp.ACTION_STOP_RECORDING
     */
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_START_RECORDING -> {
                    Log.i(TAG, "ADB Command: Start recording received")
                    // Only start if not already listening to avoid duplicate sessions
                    if (!isListening) {
                        startListening()
                    }
                }
                ACTION_STOP_RECORDING -> {
                    Log.i(TAG, "ADB Command: Stop recording received")
                    // Only stop if currently listening
                    if (isListening) {
                        stopListening()
                    }
                }
            }
        }
    }

    /**
     * Called when the activity is first created.
     * 
     * This method:
     * 1. Sets up the view binding and content view
     * 2. Configures UI event handlers (buttons, switches, engine selector)
     * 3. Initializes available STT engines
     * 4. Checks for microphone permission and requests if needed
     * 5. Registers the broadcast receiver for ADB commands
     * 6. Initializes the Voice Activity Detector (VAD) with callbacks
     * 7. Initializes the Wake Word Detector with callbacks
     * 
     * Note: STT engine is not initialized here because it requires permission first.
     * It will be initialized in checkPermission() or requestPermissionLauncher callback.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout using view binding for type-safe view access
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.i(TAG, "MainActivity created")
        
        // Initialize available STT engines
        initializeAvailableEngines()
        
        // Set up all UI event handlers (button clicks, switch toggles, engine selector, etc.)
        setupUI()
        
        // Check if we have microphone permission, request if not
        checkPermission()
        
        // Register broadcast receiver to listen for ADB commands
        registerCommandReceiver()
        
        // Initialize Voice Activity Detector (VAD)
        // VAD monitors audio levels to detect when speech starts and ends
        // Parameters:
        //   - silenceThreshold: RMS value below which audio is considered silence (200)
        //   - silenceDurationMs: How long silence must last to consider speech ended (1500ms)
        //   - preSpeechBufferDurationMs: How much audio to keep before speech starts (500ms)
        vad = VoiceActivityDetector(
            silenceThreshold = 200,
            silenceDurationMs = 1500,
            preSpeechBufferDurationMs = 500
        ).apply {
            // Set up VAD callbacks to respond to speech detection events
            setListener(object : VoiceActivityDetector.VADListener {
                /**
                 * Called when VAD detects that speech has started (audio level exceeded threshold).
                 * In auto-record mode, this automatically starts speech recognition.
                 */
                override fun onSpeechStart() {
                    Log.i(TAG, "VAD: Speech start detected")
                    // Must run on UI thread since we're updating UI and starting recognition
                    runOnUiThread {
                        // Only auto-start if auto-record mode is enabled and we're not already listening
                        if (autoRecordMode && !isListening) {
                            startListening()
                        }
                    }
                }

                /**
                 * Called when VAD detects that speech has ended (silence detected for configured duration).
                 * The audioData parameter contains the complete audio captured during the speech segment.
                 * In auto-record mode, we let the current recognition finish processing.
                 */
                override fun onSpeechEnd(audioData: ShortArray) {
                    Log.i(TAG, "VAD: Speech end detected (audio size: ${audioData.size} samples)")
                    runOnUiThread {
                        if (autoRecordMode && isListening) {
                            // Let current recognition finish processing the speech
                            // VAD will automatically trigger the next recording when new speech is detected
                            // No need to manually stop - STT engine will finish and call onResults()
                        }
                    }
                }

                /**
                 * Called during silent periods when no speech is detected.
                 * No action needed - this is just informational.
                 */
                override fun onSilence() {
                    // Silent period - no action needed
                }
            })
        }
        
        // Initialize Wake Word Detector (similar to myAssistant's implementation)
        // This continuously monitors audio for specific wake words like "hey jarvis" or "alexa"
        // When a wake word is detected, recording automatically starts
        wakeWordDetector = SimpleWakeWordDetector(
            wakeWords = listOf("hey jarvis", "alexa"),  // List of wake words to detect
            threshold = 0.9f  // Confidence threshold (0.0 to 1.0) - must exceed this to trigger
        ).apply {
            // Set up wake word detection callbacks
            setListener(object : SimpleWakeWordDetector.WakeWordListener {
                /**
                 * Called when a wake word is detected in the audio stream.
                 * 
                 * @param wakeWord The specific wake word that was detected (e.g., "hey jarvis")
                 * @param score The confidence score (0.0 to 1.0) indicating detection certainty
                 */
                override fun onWakeWordDetected(wakeWord: String, score: Float) {
                    Log.i(TAG, "Wake word detected: '$wakeWord' (score: $score)")
                    // Must run on UI thread for UI updates and Toast
                    runOnUiThread {
                        // Show user feedback that wake word was detected
                        Toast.makeText(
                            this@MainActivity,
                            "Wake word detected: $wakeWord",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Automatically start recording when wake word is detected
                        // This provides hands-free activation of speech recognition
                        if (!isListening) {
                            startListening()
                        }
                    }
                }
            })
        }
    }
    
    /**
     * Initializes the list of available STT engines.
     * 
     * This method creates instances of all available STT engines and adds them to the list.
     * Only engines that are available on the device will be added.
     */
    private fun initializeAvailableEngines() {
        // Android Native SpeechRecognizer
        val androidEngine = AndroidSpeechRecognizerEngine(this)
        if (androidEngine.isAvailable()) {
            availableEngines.add(androidEngine)
            Log.i(TAG, "Android Native STT engine available")
        }
        
        // Whisper STT Engine (whisper.cpp - on-device)
        // Model file should be in app/src/main/assets/models/ (e.g., ggml-base.bin)
        val whisperEngine = WhisperSTTEngine(
            context = this,
            modelPath = null, // null = use default from assets/models/, or provide path to model file
            useLocalWhisperCpp = true // Always use local whisper.cpp
        )
        // Always add Whisper engine to the list - let it fail gracefully if library isn't loaded
        // This allows users to see and select it, and they'll get an error message if it doesn't work
        availableEngines.add(whisperEngine)
        if (whisperEngine.isAvailable()) {
            Log.i(TAG, "Whisper STT engine (whisper.cpp) available")
        } else {
            Log.w(TAG, "Whisper STT engine added but may not be fully functional - ensure whisper.cpp native library is built")
        }
        
        Log.i(TAG, "Total available STT engines: ${availableEngines.size}")
    }
    
    /**
     * Registers the broadcast receiver to listen for ADB commands.
     * 
     * This sets up the receiver to listen for ACTION_START_RECORDING and ACTION_STOP_RECORDING
     * broadcast intents. These can be sent via ADB commands for remote control.
     * 
     * Note: On Android 13+ (API 33+), receivers must be explicitly marked as exported.
     * We use RECEIVER_EXPORTED flag for Android 13+ to allow external broadcasts.
     */
    private fun registerCommandReceiver() {
        // Create intent filter to specify which broadcast actions we want to receive
        val filter = IntentFilter().apply {
            addAction(ACTION_START_RECORDING)
            addAction(ACTION_STOP_RECORDING)
        }
        // Android 13+ (API 33+) requires explicit export flag for receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // RECEIVER_EXPORTED allows external apps/ADB to send broadcasts to this receiver
            registerReceiver(commandReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            // Pre-Android 13: receivers are exported by default
            @Suppress("DEPRECATION")
            registerReceiver(commandReceiver, filter)
        }
        Log.i(TAG, "Command receiver registered for ADB commands")
    }

    /**
     * Sets up all UI event handlers and click listeners.
     * 
     * This method configures:
     * - Engine selector: Allows user to choose STT engine
     * - Record button: Toggles recording on/off
     * - Clear button: Clears the transcription text
     * - Auto-record switch: Enables/disables VAD-based auto-recording
     * - Wake word switch: Enables/disables wake word detection
     * 
     * The switches have their entire layout containers clickable for better UX.
     */
    private fun setupUI() {
        // Set up STT engine selector
        val engineNames = availableEngines.map { it.getName() }
        Log.i(TAG, "Setting up engine selector with ${engineNames.size} engines: $engineNames")
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, engineNames)
        binding.engineSpinner.setAdapter(adapter)
        
        // Enable the dropdown to be clickable
        binding.engineSpinner.setOnClickListener {
            binding.engineSpinner.showDropDown()
        }
        
        // Select first available engine by default
        if (availableEngines.isNotEmpty()) {
            val defaultEngineName = availableEngines[0].getName()
            binding.engineSpinner.setText(defaultEngineName, false)
            Log.i(TAG, "Default STT engine selected: $defaultEngineName")
        } else {
            Log.w(TAG, "No STT engines available!")
        }
        
        // When engine selection changes, reinitialize the STT engine
        binding.engineSpinner.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Engine selection clicked, position: $position, available engines: ${availableEngines.size}")
            if (position < availableEngines.size) {
                val selectedEngine = availableEngines[position]
                Log.i(TAG, "STT engine changed to: ${selectedEngine.getName()}")
                // Destroy current engine
                sttEngine?.destroy()
                sttEngine = null
                // Initialize new engine if permission is granted
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    initializeSTTEngine()
                }
                Toast.makeText(this, "STT Engine: ${selectedEngine.getName()}", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Invalid engine position: $position (max: ${availableEngines.size - 1})")
            }
        }
        
        // Record button: Toggle recording state
        // When not listening, starts recording. When listening, stops recording.
        binding.recordButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // Clear button: Erases all transcription text from the display
        binding.clearButton.setOnClickListener {
            binding.transcriptionText.text = ""
            Log.d(TAG, "Transcription cleared")
        }
        
        // Make the entire auto-record layout clickable to toggle the switch
        // This provides a larger click target for better usability
        binding.autoRecordLayout.setOnClickListener {
            binding.autoRecordSwitch.isChecked = !binding.autoRecordSwitch.isChecked
        }
        
        // Auto-record switch: Enables/disables Voice Activity Detection (VAD) mode
        // When enabled, VAD monitors audio and automatically starts recording when speech is detected
        binding.autoRecordSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableAutoRecordMode(isChecked)
            // Provide user feedback about the mode change
            if (isChecked) {
                Toast.makeText(this, "Auto-record enabled - VAD will detect speech automatically", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Auto-record disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Make the entire wake word layout clickable to toggle the switch
        // This provides a larger click target for better usability
        binding.wakeWordLayout.setOnClickListener {
            binding.wakeWordSwitch.isChecked = !binding.wakeWordSwitch.isChecked
        }
        
        // Wake word switch: Enables/disables wake word detection mode
        // When enabled, continuously monitors audio for wake words ("hey jarvis", "alexa")
        // and automatically starts recording when detected
        binding.wakeWordSwitch.setOnCheckedChangeListener { _, isChecked ->
            enableWakeWordMode(isChecked)
            // Provide user feedback about the mode change and remind them of wake words
            if (isChecked) {
                Toast.makeText(this, "Wake word detection enabled - say 'hey jarvis' or 'alexa'", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Wake word detection disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Checks if RECORD_AUDIO permission is granted.
     * 
     * If permission is already granted, initializes the STT engine immediately.
     * If not granted, launches the permission request dialog using the modern Activity Result API.
     * 
     * This is called in onCreate() to ensure we have permission before attempting to use
     * the microphone for speech recognition, VAD, or wake word detection.
     */
    private fun checkPermission() {
        when {
            // Permission already granted - initialize STT engine right away
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeSTTEngine()
            }
            // Permission not granted - request it from the user
            else -> {
                // This will show Android's permission dialog
                // The result will be handled by requestPermissionLauncher callback
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Initializes the selected STT engine.
     * 
     * This creates and configures the STT engine with a listener
     * that handles all speech recognition events (start, results, errors, etc.).
     * 
     * Note: This requires RECORD_AUDIO permission to be granted first.
     * The engine is selected from the UI spinner.
     */
    private fun initializeSTTEngine() {
        // Get selected engine from spinner
        val selectedEngineName = binding.engineSpinner.text.toString()
        val selectedEngine = availableEngines.find { it.getName() == selectedEngineName }
            ?: availableEngines.firstOrNull()
        
        if (selectedEngine == null) {
            Log.e(TAG, "No STT engine available")
            Toast.makeText(
                this,
                "No STT engine available",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Destroy previous engine if exists
        sttEngine?.destroy()
        
        // Initialize new engine
        sttEngine = selectedEngine
        sttEngine?.initialize(createSTTListener())
        Log.i(TAG, "STT engine initialized: ${selectedEngine.getName()}")
    }

    /**
     * Starts speech recognition listening.
     * 
     * This method:
     * 1. Ensures STT engine is initialized (creates if needed)
     * 2. Starts listening for speech input
     * 3. Updates the UI to show "Listening..." state
     * 
     * This can be called:
     * - Manually via the record button
     * - Automatically by VAD when speech is detected (auto-record mode)
     * - Automatically when a wake word is detected
     * - Remotely via ADB broadcast command
     */
    fun startListening() {
        // Ensure STT engine is initialized before starting
        // This handles the case where permission was granted after onCreate
        if (sttEngine == null) {
            initializeSTTEngine()
        }
        
        if (sttEngine == null) {
            Log.e(TAG, "STT engine not available")
            Toast.makeText(this, "STT engine not available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.i(TAG, "Starting speech recognition with ${sttEngine?.getName()}...")
            // Start the recognition session
            // The STT listener will receive callbacks for all events
            sttEngine?.startListening(
                language = Locale.getDefault().toString(),
                preferOffline = true
            )
            // Update state flag to prevent duplicate sessions
            isListening = true
            // Update UI to show "Listening..." status and change button text
            updateUI()
        } catch (e: Exception) {
            // Handle any errors during recognition start
            // Common causes: permission issues, engine not initialized, device issues
            Log.e(TAG, "Error starting speech recognition", e)
            Toast.makeText(this, "Error starting speech recognition: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Enables or disables auto-record mode using Voice Activity Detection (VAD).
     * 
     * When enabled:
     * - VAD continuously monitors audio levels
     * - Automatically starts recording when speech is detected (audio exceeds threshold)
     * - Provides hands-free operation - no need to press the record button
     * 
     * When disabled:
     * - VAD stops monitoring audio
     * - Recording must be started manually via button or other triggers
     * 
     * @param enabled True to enable auto-record mode, false to disable
     */
    fun enableAutoRecordMode(enabled: Boolean) {
        autoRecordMode = enabled
        Log.i(TAG, "Auto-record mode: $enabled")
        if (enabled) {
            // Start VAD monitoring - it will automatically trigger recording when speech is detected
            vad?.startDetection()
            Log.i(TAG, "VAD started - will auto-detect speech")
        } else {
            // Stop VAD monitoring to save resources
            vad?.stopDetection()
            Log.i(TAG, "VAD stopped")
        }
    }
    
    /**
     * Enables or disables wake word detection mode.
     * 
     * When enabled:
     * - Wake word detector continuously monitors audio in the background
     * - When a wake word is detected ("hey jarvis" or "alexa"), recording automatically starts
     * - Provides voice-activated hands-free operation
     * 
     * When disabled:
     * - Wake word detection stops to save battery and resources
     * 
     * This method checks for microphone permission before starting detection.
     * If permission is not granted, it shows an error and disables the switch.
     * 
     * @param enabled True to enable wake word detection, false to disable
     */
    fun enableWakeWordMode(enabled: Boolean) {
        wakeWordMode = enabled
        Log.i(TAG, "Wake word mode: $enabled")
        if (enabled) {
            // Wake word detection requires microphone permission
            // Check permission first before attempting to start
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted, starting wake word detection...")
                // Start the wake word detector - it runs in a background thread
                wakeWordDetector?.startDetection()
                
                // Verify it actually started successfully
                // This helps catch initialization errors early
                if (wakeWordDetector?.isDetecting() == true) {
                    Log.i(TAG, "Wake word detection started successfully - listening for 'hey jarvis' or 'alexa'")
                    Toast.makeText(
                        this,
                        "Wake word detection active",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Detection failed to start - could be due to AudioRecord initialization issues
                    Log.e(TAG, "Wake word detection failed to start")
                    Toast.makeText(
                        this,
                        "Failed to start wake word detection. Check logs.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Reset UI to reflect actual state
                    binding.wakeWordSwitch.isChecked = false
                    wakeWordMode = false
                }
            } else {
                // Permission not granted - can't start wake word detection
                Log.w(TAG, "Microphone permission required for wake word detection")
                Toast.makeText(
                    this,
                    "Microphone permission required for wake word detection",
                    Toast.LENGTH_SHORT
                ).show()
                // Reset UI to reflect actual state
                binding.wakeWordSwitch.isChecked = false
                wakeWordMode = false
            }
        } else {
            // Disable wake word detection - stop monitoring to save resources
            wakeWordDetector?.stopDetection()
            Log.i(TAG, "Wake word detection stopped")
        }
    }

    /**
     * Stops the current speech recognition session.
     * 
     * This method:
     * - Stops listening for speech input
     * - Updates the isListening flag
     * - Updates the UI to show "Ready" state
     * 
     * Note: If speech was in progress, the recognizer will still process it and
     * call onResults() with the final transcription. This just stops capturing new audio.
     */
    fun stopListening() {
        Log.i(TAG, "Stopping speech recognition...")
        // Stop capturing audio - any speech already captured will still be processed
        sttEngine?.stopListening()
        // Update state flag
        isListening = false
        // Update UI to reflect stopped state
        updateUI()
    }

    /**
     * Updates the UI to reflect the current listening state.
     * 
     * When listening:
     * - Record button text changes to "Stop Recording"
     * - Status text shows "Listening..."
     * 
     * When not listening:
     * - Record button text changes to "Start Recording"
     * - Status text shows "Ready"
     */
    private fun updateUI() {
        if (isListening) {
            binding.recordButton.text = "Stop Recording"
            binding.statusText.text = "Listening..."
        } else {
            binding.recordButton.text = "Start Recording"
            binding.statusText.text = "Ready"
        }
    }

    /**
     * Creates and returns an STTListener that handles all speech recognition events.
     * 
     * This listener receives callbacks from the STT engine throughout the
     * recognition lifecycle:
     * - onReadyForSpeech: Engine is ready to receive audio
     * - onBeginningOfSpeech: Speech input has started
     * - onRmsChanged: Audio level changes (can be used for visual feedback)
     * - onEndOfSpeech: User stopped speaking
     * - onResults: Final transcription results are available
     * - onPartialResults: Intermediate transcription results (real-time)
     * - onError: An error occurred during recognition
     * 
     * @return An STTListener instance configured to handle all recognition events
     */
    private fun createSTTListener(): STTEngine.STTListener {
        return object : STTEngine.STTListener {
            /**
             * Called when the engine is ready to receive speech input.
             * This happens right after startListening() is called, before any audio is captured.
             */
            override fun onReadyForSpeech() {
                Log.d(TAG, "STT: Ready for speech")
                binding.statusText.text = "Ready for speech"
            }

            /**
             * Called when the engine detects that speech has begun.
             * This is triggered when audio levels exceed a threshold indicating speech.
             */
            override fun onBeginningOfSpeech() {
                Log.i(TAG, "STT: Beginning of speech detected")
                binding.statusText.text = "Listening..."
            }

            /**
             * Called periodically with the current audio level (RMS in dB).
             * This can be used to show a visual audio level indicator.
             * 
             * @param rmsdB Root Mean Square audio level in decibels
             */
            override fun onRmsChanged(rmsdB: Float) {
                // Log audio level periodically (throttle to avoid spam)
                // This could be used to update a visual audio level indicator
                // Log.d(TAG, "STT: RMS changed to $rmsdB dB")
            }

            /**
             * Called when the engine detects that speech has ended.
             * This happens when the user stops speaking or after a timeout.
             * The engine will now process the captured audio and generate results.
             */
            override fun onEndOfSpeech() {
                Log.i(TAG, "STT: End of speech detected, processing...")
                binding.statusText.text = "Processing..."
            }

            /**
             * Called when an error occurs during recognition.
             * 
             * @param error Error code
             * @param message Human-readable error message
             */
            override fun onError(error: Int, message: String) {
                Log.e(TAG, "STT error: $message (code: $error)")
                // Update UI to show error message
                binding.statusText.text = "Error: $message"
                // Reset listening state
                isListening = false
                updateUI()
            }

            /**
             * Called when final recognition results are available.
             * 
             * This is the main callback that provides the complete transcription.
             * Results are ordered by confidence (first result is most likely).
             * 
             * After results are received:
             * - Transcription is appended to the text view
             * - Listening state is reset
             * - If auto-record mode is enabled, VAD is restarted to detect next speech
             * 
             * @param results List of transcription results, ordered by confidence
             */
            override fun onResults(results: List<String>) {
                if (results.isNotEmpty()) {
                    // Get the top result (highest confidence)
                    val transcription = results[0]
                    Log.i(TAG, "Transcription available: \"$transcription\"")
                    // Append to the transcription text view (add newline for readability)
                    binding.transcriptionText.append("$transcription\n")
                    binding.statusText.text = "Transcription complete"
                } else {
                    // No results - user may not have spoken, or recognition failed silently
                    Log.w(TAG, "Transcription results empty")
                }
                // Reset listening state
                isListening = false
                updateUI()
                
                // If auto-record mode is enabled, restart VAD to detect the next speech segment
                // This allows continuous automatic recording without manual intervention
                if (autoRecordMode) {
                    vad?.startDetection()
                }
            }

            /**
             * Called with partial/intermediate recognition results.
             * 
             * This provides real-time transcription as the user speaks, before final results.
             * Useful for showing live feedback. Results may change as more audio is processed.
             * 
             * @param partialResults List of partial transcription results
             */
            override fun onPartialResults(partialResults: List<String>) {
                if (partialResults.isNotEmpty()) {
                    val partialText = partialResults[0]
                    Log.d(TAG, "Partial transcription: \"$partialText\"")
                    // Show partial result in status text for real-time feedback
                    binding.statusText.text = "Partial: $partialText"
                }
            }
        }
    }

    /**
     * Called when the activity is being destroyed.
     * 
     * This is the cleanup method that:
     * - Unregisters the broadcast receiver (prevents memory leaks)
     * - Stops VAD detection (releases audio resources)
     * - Stops wake word detection (releases audio resources)
     * - Destroys the STT engine (releases system resources)
     * 
     * It's important to clean up all resources to prevent memory leaks and
     * ensure audio resources are released for other apps to use.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "MainActivity destroyed")
        // Unregister broadcast receiver to prevent memory leaks
        unregisterReceiver(commandReceiver)
        // Stop VAD and release audio resources
        vad?.stopDetection()
        // Stop wake word detection and release audio resources
        wakeWordDetector?.stopDetection()
        // Destroy STT engine to release system resources
        sttEngine?.destroy()
    }
}
