# Android STT App

An Android application for on-device speech-to-text transcription, compatible with Android 14.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-14+-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org/)

## Features

- **Multiple STT Engines**: Supports Android Native and Whisper (faster-whisper)
- **On-device transcription**: Uses Android's built-in SpeechRecognizer API with offline mode support
- **Whisper support**: Optional Whisper STT via faster-whisper server or whisper.cpp (coming soon)
- **Real-time transcription**: Shows partial results as you speak (Android Native)
- **Voice Activity Detection (VAD)**: Automatically detects when speech starts and stops
- **Wake word detection**: Hands-free activation using wake words like "hey jarvis" or "alexa"
- **ADB remote control**: Control recording via ADB commands for automation
- **Simple UI**: Clean interface with record button, engine selector, and transcription display
- **Android 14 compatible**: Built for Android 14 (API 34)

## How It Works

This section provides a comprehensive explanation of the app's architecture and functionality.

### Architecture Overview

The app consists of several key components that work together to provide speech recognition:

1. **MainActivity** - Main UI and orchestration layer
2. **SpeechRecognizer** - Android's built-in speech recognition engine
3. **VoiceActivityDetector (VAD)** - Detects speech start/end based on audio levels
4. **SimpleWakeWordDetector** - Detects wake words to trigger recording
5. **BroadcastReceiver** - Handles ADB remote control commands

### Component Details

#### MainActivity

The `MainActivity` class is the central coordinator that:
- Manages the UI and user interactions
- Handles permission requests for microphone access
- Initializes and coordinates all other components
- Processes speech recognition results and updates the UI

**Key Responsibilities:**
- **Permission Management**: Requests and handles `RECORD_AUDIO` permission using the modern Activity Result API
- **UI Management**: Updates button states, status text, and transcription display
- **Mode Management**: Handles three recording modes:
  - **Manual Mode**: User presses button to start/stop recording
  - **Auto-Record Mode**: VAD automatically starts recording when speech is detected
  - **Wake Word Mode**: Wake word detector automatically starts recording when wake words are detected
- **Speech Recognition Lifecycle**: Manages the SpeechRecognizer instance and handles all recognition events

**Recording Modes:**

1. **Manual Recording**:
   - User taps "Start Recording" button
   - `startListening()` is called
   - SpeechRecognizer begins capturing audio
   - User taps "Stop Recording" when finished
   - Final transcription appears in the text view

2. **Auto-Record Mode (VAD)**:
   - User enables "Auto-record (VAD)" switch
   - VAD starts monitoring audio in the background
   - When speech is detected (audio level exceeds threshold), recording automatically starts
   - When silence is detected (audio level below threshold for 1.5 seconds), current recognition finishes
   - VAD automatically restarts to detect the next speech segment
   - Provides hands-free continuous recording

3. **Wake Word Mode**:
   - User enables "Wake Word Detection" switch
   - Wake word detector starts monitoring audio in the background
   - When a wake word is detected ("hey jarvis" or "alexa"), recording automatically starts
   - User can then speak naturally, and recording will capture their speech
   - Provides voice-activated hands-free operation

#### SpeechRecognizer Integration

The app uses Android's native `SpeechRecognizer` API, which:
- Supports both online and offline recognition (offline requires language packs)
- Provides real-time partial results as the user speaks
- Handles all audio capture internally
- Returns final transcription results when speech ends

**Recognition Flow:**
1. `startListening()` creates an Intent with recognition parameters
2. SpeechRecognizer starts capturing audio from the microphone
3. `onBeginningOfSpeech()` callback indicates speech has started
4. `onPartialResults()` provides real-time transcription updates
5. `onEndOfSpeech()` indicates user stopped speaking
6. `onResults()` provides final transcription (ordered by confidence)
7. Transcription is appended to the text view

**Recognition Configuration:**
- **Language Model**: `LANGUAGE_MODEL_FREE_FORM` (best for conversational speech)
- **Language**: Device's default locale
- **Offline Preference**: `EXTRA_PREFER_OFFLINE = true` (uses offline if available)
- **Partial Results**: `EXTRA_PARTIAL_RESULTS = true` (enables real-time updates)

#### Voice Activity Detector (VAD)

The `VoiceActivityDetector` class monitors audio to detect when speech starts and ends:

**How VAD Works:**
1. Continuously captures audio from the microphone in a background thread
2. Calculates RMS (Root Mean Square) for each audio buffer
3. Compares RMS to a threshold (default: 200) to determine speech vs silence
4. Detects speech start when RMS exceeds threshold
5. Detects speech end when RMS falls below threshold for sustained period (default: 1.5 seconds)
6. Maintains a pre-speech buffer (500ms) to capture audio before detection

**VAD Parameters:**
- **Sample Rate**: 16kHz (standard for speech)
- **Silence Threshold**: 200 RMS (below this = silence)
- **Silence Duration**: 1500ms (how long silence must persist to end speech)
- **Pre-Speech Buffer**: 500ms (audio to keep before speech starts)

**VAD Callbacks:**
- `onSpeechStart()`: Called when speech is detected (RMS > threshold)
- `onSpeechEnd(audioData)`: Called when speech ends (silence for configured duration)
- `onSilence()`: Called during silent periods

**Use Case:**
VAD is used in auto-record mode to automatically start recording when the user begins speaking, eliminating the need to press a button.

#### Wake Word Detection

The `SimpleWakeWordDetector` class monitors audio for specific wake words:

**How Wake Word Detection Works:**
1. Continuously captures audio from the microphone in a background thread
2. Processes each audio buffer through the detector (currently placeholder implementation)
3. Checks if any wake word score exceeds the threshold (default: 0.9)
4. Calls listener callback when wake word is detected
5. Automatically starts speech recognition

**Current Implementation:**
- Uses simple energy-based detection (placeholder)
- Not a real ML-based wake word detector
- Demonstrates the detection loop architecture
- Can be easily replaced with TensorFlow Lite, ONNX Runtime, or Porcupine

**Production Implementation Options:**
1. **TensorFlow Lite**: Convert openwakeword models to TFLite format
2. **ONNX Runtime**: Use openwakeword ONNX models directly
3. **Porcupine/Picovoice**: Commercial SDK with pre-trained models
4. **Custom Models**: Train your own wake word detection models

**Wake Words:**
- "hey jarvis"
- "alexa"

**Use Case:**
Wake word detection provides voice-activated hands-free operation - the user says a wake word, and recording automatically starts.

#### ADB Remote Control

The app includes a `BroadcastReceiver` that listens for ADB commands:

**Supported Commands:**
- `ACTION_START_RECORDING`: Starts speech recognition remotely
- `ACTION_STOP_RECORDING`: Stops speech recognition remotely

**Usage:**
```bash
# Start recording via ADB
adb shell am broadcast -a com.sttapp.ACTION_START_RECORDING

# Stop recording via ADB
adb shell am broadcast -a com.sttapp.ACTION_STOP_RECORDING
```

**Use Cases:**
- Automation scripts
- Testing and debugging
- Remote control from other devices
- Integration with other tools

### Data Flow

#### Manual Recording Flow:
```
User taps "Start Recording"
  → startListening()
    → SpeechRecognizer.startListening()
      → Audio capture begins
      → onBeginningOfSpeech()
      → onPartialResults() (real-time updates)
      → User stops speaking
      → onEndOfSpeech()
      → onResults() (final transcription)
        → Transcription appended to text view
```

#### Auto-Record (VAD) Flow:
```
User enables "Auto-record" switch
  → VAD.startDetection()
    → Background thread monitors audio
    → RMS calculation for each buffer
    → Speech detected (RMS > threshold)
      → onSpeechStart()
        → startListening() (automatic)
          → Speech recognition begins
    → Silence detected (RMS < threshold for 1.5s)
      → onSpeechEnd()
        → Current recognition finishes
        → VAD restarts for next detection
```

#### Wake Word Flow:
```
User enables "Wake Word Detection" switch
  → WakeWordDetector.startDetection()
    → Background thread monitors audio
    → Audio processed through detector
    → Wake word detected (score > threshold)
      → onWakeWordDetected()
        → startListening() (automatic)
          → Speech recognition begins
```

### Threading Model

The app uses multiple threads to ensure smooth operation:

1. **Main/UI Thread**: Handles all UI updates and user interactions
2. **VAD Thread**: Background thread for continuous audio monitoring (VAD)
3. **Wake Word Thread**: Background thread for continuous audio monitoring (wake words)
4. **SpeechRecognizer Threads**: Managed internally by Android's SpeechRecognizer

**Thread Safety:**
- All UI updates are performed on the main thread using `runOnUiThread()`
- Audio capture runs in background threads to avoid blocking the UI
- Callbacks from background threads switch to UI thread before updating UI

### Resource Management

The app properly manages resources to prevent leaks:

1. **AudioRecord**: Released when VAD/wake word detection stops
2. **SpeechRecognizer**: Destroyed in `onDestroy()`
3. **BroadcastReceiver**: Unregistered in `onDestroy()`
4. **Threads**: Stopped gracefully when detection ends

### Error Handling

The app handles various error conditions:

1. **Permission Denied**: Shows toast message and disables features requiring permission
2. **Speech Recognition Errors**: Maps error codes to user-friendly messages
3. **AudioRecord Errors**: Logs errors and stops detection gracefully
4. **Initialization Failures**: Validates state and shows error messages

### Logging

Comprehensive logging is provided for debugging:

- **Main App**: `STTApp` tag
- **VAD**: `VAD` tag
- **Wake Word**: `WakeWordDetector` tag

**Viewing Logs:**
```bash
# All app logs
adb logcat -s STTApp

# VAD events
adb logcat -s VAD:*

# Wake word events
adb logcat -s WakeWordDetector:*
```

## Requirements

- Android Studio Hedgehog or later
- Android SDK 34 (Android 14)
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)

## Building the APK

### Option 1: Using Android Studio (Recommended)

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Build the project: `Build > Make Project`
4. Generate APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
5. The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Using Command Line

First, ensure you have the Gradle wrapper. If not, generate it:
```bash
gradle wrapper
```

Then build the APK:
```bash
./gradlew assembleDebug
```

Or use the provided build script:
```bash
./build.sh
```

The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

### Option 3: Direct ADB Install (if device is connected)

```bash
./gradlew installDebug
```

This will build and install the app directly on a connected Android device.

## Installation

### Option 1: Install via ADB (Recommended - Easiest)

If your Android device is connected via USB with USB debugging enabled:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

This will install the app directly on your device.

### Option 2: Manual Installation

1. **Transfer the APK to your device:**
   - Copy `app/build/outputs/apk/debug/app-debug.apk` to your Android device
   - You can use USB file transfer, email, cloud storage, or any file sharing method

2. **Enable installation from unknown sources (Android 14):**
   - On your Android device, go to: **Settings > Apps > Special app access > Install unknown apps**
   - Select the app you'll use to install the APK (e.g., "Files", "Chrome", "Gmail")
   - Toggle **"Allow from this source"** to ON
   - Alternatively, when you tap the APK file, Android 14 will prompt you to allow installation from that source

3. **Install the APK:**
   - Open a file manager on your device
   - Navigate to where you saved the APK file
   - Tap the APK file
   - Tap **"Install"** when prompted
   - Wait for installation to complete
   - Tap **"Open"** to launch the app, or find "STT App" in your app drawer

## First Launch & Usage

### First Time Setup:

1. **Launch the app** from your app drawer (look for "STT App")

2. **Grant microphone permission:**
   - When you first tap "Start Recording", Android will prompt for microphone permission
   - Tap **"Allow"** or **"While using the app"**
   - This permission is required for speech recognition

3. **Optional - Enable offline mode:**
   - For truly on-device transcription (no internet needed):
   - Go to: **Settings > System > Languages & input > Offline speech recognition**
   - Download your language pack (e.g., "English (US)")
   - The app will automatically use offline mode when available

### Using the App:

1. Tap **"Start Recording"** button
2. Speak clearly into your device's microphone
3. Watch for partial transcriptions in the status area
4. Tap **"Stop Recording"** when finished
5. Your full transcription will appear in the text area
6. Tap **"Clear"** to clear the transcription and start fresh

### Tips:

- Speak clearly and at a normal pace
- Reduce background noise for better accuracy
- The app works best in quiet environments
- If offline mode isn't available, the app will use online recognition (requires internet)

## Offline Mode

The app uses `EXTRA_PREFER_OFFLINE` flag to prefer offline recognition. For offline mode to work:

1. Download offline language packs in Android Settings:
   - Settings > System > Languages & input > Offline speech recognition
   - Download your language pack

2. The app will automatically use offline mode when available

## Permissions

- `RECORD_AUDIO`: Required for capturing audio input
- `INTERNET`: Required for online speech recognition (fallback)
- `ACCESS_NETWORK_STATE`: To check network availability

## Notes

- The app uses Android's built-in SpeechRecognizer API (on-device solution)
- This is Android's native STT implementation, different from the Whisper-based STT in myAssistant
- Offline mode requires language packs to be installed on the device
- If offline packs are not available, the app will use online recognition as fallback
- Partial results are shown in real-time during recording
- The app is fully on-device when offline language packs are installed

## Troubleshooting

### JDK Image Transformation Error

If you encounter an error like:
```
Failed to transform core-for-system-modules.jar to match attributes
Error while executing process .../jlink
```

Try these steps:

1. **Clean the build:**
   ```bash
   ./clean-build.sh
   ```

2. **In Android Studio:**
   - File > Invalidate Caches / Restart
   - File > Sync Project with Gradle Files
   - Build > Clean Project
   - Build > Rebuild Project

3. **If the issue persists:**
   - Check that Android Studio is using JDK 17 (File > Project Structure > SDK Location > JDK location)
   - Ensure you have the latest Android SDK Platform 34 installed
   - Try updating Android Studio to the latest version

### Build Configuration

The project uses:
- Android Gradle Plugin: 8.0.2 (stable version)
- Gradle: 8.0
- Kotlin: 1.9.0
- Compile SDK: 34 (Android 14)
- Target SDK: 34
- Min SDK: 24 (Android 7.0)

**Important:** If you're still seeing JDK image errors:

1. **Set JDK to version 17 in Android Studio:**
   - File > Project Structure > SDK Location
   - Under "JDK location", select JDK 17 (not JDK 21)
   - Android Studio's embedded JDK 17 path is usually: `/Applications/Android Studio.app/Contents/jbr/Contents/Home` (but select JDK 17 specifically)
   - Or download JDK 17 from: https://adoptium.net/temurin/releases/?version=17

2. **Run the clean script:**
   ```bash
   ./clean-build.sh
   ```

3. **Then in Android Studio:**
   - File > Invalidate Caches / Restart
   - File > Sync Project with Gradle Files
   - Build > Rebuild Project

## ADB Commands & Logging

The app supports ADB commands for remote control and comprehensive logging.

### ADB Commands

**Start recording:**
```bash
adb shell am broadcast -a com.sttapp.ACTION_START_RECORDING
```

**Stop recording:**
```bash
adb shell am broadcast -a com.sttapp.ACTION_STOP_RECORDING
```

### Viewing Logs

**All app logs:**
```bash
adb logcat -s STTApp
```

**Transcription events:**
```bash
adb logcat -s STTApp:* | grep -i "transcription"
```

**VAD events:**
```bash
adb logcat -s STTApp:* VAD:*
```

See [ADB_COMMANDS.md](ADB_COMMANDS.md) for detailed documentation.

### Logging Features

The app logs:
- ✅ When recording starts/stops
- ✅ When transcription is available
- ✅ The actual transcription text
- ✅ VAD (Voice Activity Detection) events
- ✅ Errors and warnings

All logs use the `STTApp` tag, and VAD uses the `VAD` tag.

## Voice Activity Detector (VAD)

The app includes a VAD similar to myAssistant that can automatically detect speech start and end:

1. **Enable Auto-record mode**: Toggle the "Auto-record (VAD)" switch in the app
2. **Automatic detection**: VAD monitors audio and automatically starts recording when speech is detected
3. **Auto-stop**: Recording stops automatically after 1.5 seconds of silence
4. **Configurable**: Uses RMS threshold of 200 and silence duration of 1.5 seconds (similar to myAssistant)

The VAD implementation mirrors myAssistant's approach:
- RMS-based silence detection
- Pre-speech buffer (500ms)
- Configurable silence threshold and duration

## Wake Word Detection

The app includes wake word detection similar to myAssistant's openwakeword implementation:

1. **Enable Wake Word mode**: Toggle the "Wake Word Detection" switch in the app
2. **Supported wake words**: "hey jarvis" and "alexa" (same as myAssistant)
3. **Automatic activation**: When a wake word is detected, recording starts automatically
4. **Threshold**: Uses 0.9 threshold (same as myAssistant default)

**How it works:**
- Continuously monitors audio in the background
- Detects wake words using audio pattern matching
- Automatically starts STT recording when wake word is detected
- Logs all wake word detections to logcat

**Note**: The current implementation uses a simple audio pattern matcher. For production use, you can:
- Replace with TensorFlow Lite models (convert openwakeword ONNX models)
- Use Porcupine/Picovoice for commercial-grade wake word detection
- Integrate ONNX Runtime to use openwakeword models directly

Wake word events are logged with the `WakeWordDetector` tag.

## STT Engine Selection

The app now supports multiple STT engines that can be selected from the UI:

### Android Native (Default)
- **Native Android integration**: Better performance and battery efficiency
- **On-device support**: Works offline with language packs
- **No external dependencies**: No need to bundle large model files
- **System-level optimization**: Leverages Android's optimized speech recognition
- **Real-time partial results**: Shows transcription as you speak

### Whisper (faster-whisper)
- **High accuracy**: State-of-the-art speech recognition
- **Server-based**: Requires a faster-whisper server (see setup below)
- **Future**: whisper.cpp integration for on-device processing (coming soon)

To switch engines, use the "STT Engine" dropdown in the app UI.

## Whisper Setup (whisper.cpp)

The app uses **whisper.cpp** for on-device Whisper processing. This provides:
- **Fully offline**: No network required
- **On-device processing**: All processing happens on the device
- **Privacy**: Audio never leaves the device
- **High accuracy**: State-of-the-art speech recognition

### Quick Start

1. **Add whisper.cpp to the project** (see [WHISPER_CPP_SETUP.md](WHISPER_CPP_SETUP.md) for details):
   ```bash
   git submodule add https://github.com/ggerganov/whisper.cpp.git app/src/main/cpp/whisper.cpp
   git submodule update --init --recursive
   ```

2. **Download a Whisper model**:
   ```bash
   cd app/src/main/cpp/whisper.cpp
   ./models/download-ggml-model.sh base
   ```

3. **Copy model to assets**:
   ```bash
   mkdir -p app/src/main/assets/models
   cp app/src/main/cpp/whisper.cpp/models/ggml-base.bin app/src/main/assets/models/
   ```

4. **Complete the integration**:
   - Update `app/src/main/cpp/CMakeLists.txt` (uncomment whisper.cpp sections)
   - Update `app/src/main/cpp/whisper_jni.cpp` (implement actual whisper.cpp calls)
   - See [WHISPER_CPP_SETUP.md](WHISPER_CPP_SETUP.md) for detailed instructions

5. **Build and run**:
   - Sync Gradle in Android Studio
   - Build the project
   - Select "Whisper (whisper.cpp)" from the STT Engine dropdown

### Model Recommendations

- **tiny**: Fastest, lowest accuracy (~39MB) - Good for testing
- **base**: Good balance (~142MB) - **Recommended for most use cases**
- **small**: Better accuracy (~466MB) - For high-accuracy needs
- **medium/large**: Not recommended for mobile (too large/slow)

### Current Status

The integration structure is complete, but you need to:
1. Add whisper.cpp as a submodule or download it manually
2. Complete the JNI implementation in `whisper_jni.cpp`
3. Update `CMakeLists.txt` to include whisper.cpp source files

See [WHISPER_CPP_SETUP.md](WHISPER_CPP_SETUP.md) for step-by-step instructions.

## Comparison with myAssistant STT

This Android app now supports both:
- **Android Native**: Optimized for mobile, works offline with language packs
- **Whisper**: High-accuracy transcription (via server or future on-device support)

The myAssistant project uses Whisper (faster-whisper) which is a Python-based solution optimized for desktop/server environments. This Android app can now connect to the same faster-whisper backend for consistent transcription quality.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to this project.

## GitHub Setup

This project is ready for GitHub! Here's what's included:

- ✅ Comprehensive `.gitignore` for Android projects
- ✅ MIT License
- ✅ Contributing guidelines (`CONTRIBUTING.md`)
- ✅ Issue templates (bug reports, feature requests)
- ✅ Pull request template
- ✅ Changelog template
- ✅ Code of conduct ready (add if needed)

### Initial GitHub Setup

1. **Create a new repository on GitHub** (don't initialize with README, .gitignore, or license)

2. **Initialize git and push** (if not already done):
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Android STT App"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/androidSTTapp.git
   git push -u origin main
   ```

3. **Update placeholder values**:
   - Replace `YOUR_USERNAME` in `.github/ISSUE_TEMPLATE/config.yml` with your GitHub username
   - Update `YOUR_USERNAME` in `CHANGELOG.md` with your GitHub username
   - Update author information in `LICENSE` if desired

4. **Optional**: Add repository topics on GitHub:
   - `android`
   - `kotlin`
   - `speech-recognition`
   - `speech-to-text`
   - `voice-activity-detection`
   - `wake-word-detection`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with Android's native SpeechRecognizer API
- VAD implementation inspired by myAssistant project
- Wake word detection architecture designed for easy ML model integration

