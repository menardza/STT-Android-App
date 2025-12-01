# ADB Commands for STT App

This document describes how to control the STT App via ADB commands.

## Prerequisites

1. Enable USB debugging on your Android device
2. Connect device via USB
3. Verify connection: `adb devices`

## Available Commands

### Start Recording

Start speech recognition manually:

```bash
adb shell am broadcast -a com.sttapp.ACTION_START_RECORDING
```

### Stop Recording

Stop current speech recognition:

```bash
adb shell am broadcast -a com.sttapp.ACTION_STOP_RECORDING
```

## Viewing Logs

### Filter by App Tag

View all STT App logs:

```bash
adb logcat -s STTApp
```

### View Specific Events

**Recording events:**
```bash
adb logcat -s STTApp:* | grep -i "recording\|listening"
```

**Transcription events:**
```bash
adb logcat -s STTApp:* | grep -i "transcription"
```

**VAD (Voice Activity Detection) events:**
```bash
adb logcat -s STTApp:* VAD:*
```

**All events (comprehensive):**
```bash
adb logcat -s STTApp:* VAD:* WakeWordDetector:*
```

### Real-time Monitoring

Monitor logs in real-time:

```bash
adb logcat -s STTApp:* VAD:* | grep -E "(Starting|Stopping|Transcription|Speech)"
```

## Log Tags

The app uses the following log tags:

- **STTApp**: Main app events (recording start/stop, transcriptions, errors)
- **VAD**: Voice Activity Detector events (speech detection, silence detection)
- **WakeWordDetector**: Wake word detection events (wake word detected, scores)

## Example Log Output

```
I/STTApp: MainActivity created
I/STTApp: Command receiver registered for ADB commands
I/STTApp: Starting speech recognition...
I/STTApp: Recognition: Beginning of speech detected
I/STTApp: Recognition: End of speech detected, processing...
I/STTApp: Transcription available: "Hello world"
I/VAD: VAD: Started detection (threshold=200, silence_duration=1500ms)
I/VAD: VAD: Speech detected (RMS=450.2)
I/VAD: VAD: Silence detected after speech, processing...
I/WakeWordDetector: Wake word detection started (wakewords: hey jarvis, alexa, threshold: 0.9)
I/WakeWordDetector: Wake word detected: 'hey jarvis' (score: 0.95)
I/STTApp: Wake word detected: 'hey jarvis' (score: 0.95)
```

## Auto-Record Mode (VAD)

The app includes a Voice Activity Detector (VAD) similar to myAssistant that can automatically detect when speech starts and stops. To enable:

1. Toggle the "Auto-record (VAD)" switch in the app UI
2. The VAD will automatically start recording when speech is detected
3. Recording stops automatically after 1.5 seconds of silence

VAD events are logged with the `VAD` tag.

## Troubleshooting

### No logs appearing

Make sure the app is running and has microphone permission:

```bash
adb shell pm grant com.sttapp android.permission.RECORD_AUDIO
```

### Commands not working

Verify the broadcast receiver is registered:

```bash
adb logcat -s STTApp | grep "Command receiver"
```

You should see: `I/STTApp: Command receiver registered for ADB commands`

