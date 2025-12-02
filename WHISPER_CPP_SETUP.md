# Whisper.cpp Integration Guide

This guide explains how to complete the whisper.cpp integration for on-device speech recognition.

## Prerequisites

1. **Android Studio** with NDK installed
2. **CMake** (included with Android Studio)
3. **Git** (for submodule setup)

## Step 1: Add whisper.cpp to the Project

You have two options:

### Option A: Git Submodule (Recommended)

```bash
cd /path/to/STT-Android-App
git submodule add https://github.com/ggerganov/whisper.cpp.git app/src/main/cpp/whisper.cpp
git submodule update --init --recursive
```

### Option B: Manual Download

1. Download whisper.cpp from https://github.com/ggerganov/whisper.cpp
2. Extract it to `app/src/main/cpp/whisper.cpp/`

## Step 2: Update CMakeLists.txt

1. Open `app/src/main/cpp/CMakeLists.txt`
2. Uncomment the sections for whisper.cpp integration
3. Update paths if needed

The file should look like this after uncommenting:

```cmake
set(WHISPER_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${WHISPER_CPP_DIR}
)

set(WHISPER_SOURCES
    ${WHISPER_CPP_DIR}/whisper.cpp/whisper.cpp
    ${WHISPER_CPP_DIR}/whisper.cpp/ggml.c
    # ... other required files
)

add_library(whisper-jni SHARED
    whisper_jni.cpp
    ${WHISPER_SOURCES}
)
```

## Step 3: Complete JNI Implementation

Update `app/src/main/cpp/whisper_jni.cpp` to use actual whisper.cpp functions:

```cpp
#include "whisper.h"  // Add whisper.cpp header

// In initContext:
whisper_context *ctx = whisper_init_from_file(path);
return reinterpret_cast<jlong>(ctx);

// In processAudio:
whisper_context *ctx = reinterpret_cast<whisper_context*>(contextPtr);
// Convert audio data, call whisper_full(), extract text
```

See the whisper.cpp examples for reference implementation.

## Step 4: Download Model Files

1. Download a Whisper model in GGML format:
   ```bash
   # From whisper.cpp directory
   ./models/download-ggml-model.sh base
   ```

2. Copy the model to your Android project:
   ```bash
   mkdir -p app/src/main/assets/models
   cp whisper.cpp/models/ggml-base.bin app/src/main/assets/models/
   ```

3. Or use a smaller model for testing:
   ```bash
   ./models/download-ggml-model.sh tiny
   cp whisper.cpp/models/ggml-tiny.bin app/src/main/assets/models/
   ```

## Step 5: Update Model Name (Optional)

If you use a different model name, update `DEFAULT_MODEL_NAME` in `WhisperSTTEngine.kt`:

```kotlin
private const val DEFAULT_MODEL_NAME = "ggml-base.bin"  // or "ggml-tiny.bin"
```

## Step 6: Build and Test

1. Sync Gradle in Android Studio
2. Build the project
3. Run on a device (emulator may be slow)
4. Select "Whisper (whisper.cpp)" from the STT Engine dropdown

## Model Recommendations

- **tiny**: Fastest, lowest accuracy (~39MB)
- **base**: Good balance (~142MB)
- **small**: Better accuracy (~466MB)
- **medium**: High accuracy (~1.5GB) - may be too large for mobile
- **large**: Best accuracy (~3GB) - not recommended for mobile

For mobile devices, **tiny** or **base** are recommended.

## Troubleshooting

### Build Errors

- **Missing whisper.cpp files**: Ensure submodule is initialized
- **CMake errors**: Check that all whisper.cpp source files are listed
- **Link errors**: Verify all required libraries are linked

### Runtime Errors

- **Model not found**: Ensure model file is in `app/src/main/assets/models/`
- **Library load failed**: Check that NDK is properly configured
- **Out of memory**: Use a smaller model (tiny or base)

### Performance Issues

- Use quantized models if available
- Consider using smaller models for better performance
- Process audio in chunks rather than all at once

## Additional Resources

- [whisper.cpp GitHub](https://github.com/ggerganov/whisper.cpp)
- [whisper.cpp Android Example](https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android)
- [Android NDK Documentation](https://developer.android.com/ndk)

## Notes

- The current implementation processes audio after recording stops
- For real-time transcription, you may want to process audio in chunks
- Model files are copied from assets to internal storage on first use
- Consider implementing model quantization for better performance

