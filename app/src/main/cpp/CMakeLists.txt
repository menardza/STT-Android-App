cmake_minimum_required(VERSION 3.22.1)

project("whisper-jni")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================================
# WHISPER.CPP INTEGRATION
# ============================================================================
# whisper.cpp is added as a submodule in app/src/main/cpp/whisper.cpp
# ============================================================================

# Set whisper.cpp directory
set(WHISPER_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Configure whisper.cpp for Android (disable features not available on Android)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_COREML OFF CACHE BOOL "" FORCE)  # iOS/macOS only
set(WHISPER_OPENVINO OFF CACHE BOOL "" FORCE)  # Not needed for Android
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_FFMPEG OFF CACHE BOOL "" FORCE)

# Disable warnings for whisper.cpp (they have many warnings we don't control)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS_3RD_PARTY OFF CACHE BOOL "" FORCE)
set(WHISPER_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)  # Don't treat warnings as errors

# Add whisper.cpp as a subdirectory
# This will build ggml and whisper libraries
add_subdirectory(${WHISPER_CPP_DIR} whisper_cpp)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${WHISPER_CPP_DIR}/include
)

# Find required libraries
find_library(log-lib log)

# ============================================================================
# JNI LIBRARY
# ============================================================================

# Source files for our JNI wrapper
add_library(whisper-jni SHARED
    whisper_jni.cpp
)

# Link against whisper library (built by whisper.cpp subdirectory)
target_link_libraries(whisper-jni
    whisper
    ${log-lib}
)

# Include directories for whisper-jni
target_include_directories(whisper-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${WHISPER_CPP_DIR}/include
)

# Compiler flags
target_compile_options(whisper-jni PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-format  # Suppress format warnings from whisper.cpp
    -O3  # Optimize for performance
)

# Suppress format warnings from whisper.cpp and ggml libraries
# These are third-party libraries with their own warning policies
if (TARGET whisper)
    target_compile_options(whisper PRIVATE
        -Wno-format  # Suppress format string warnings
    )
endif()

if (TARGET ggml)
    target_compile_options(ggml PRIVATE
        -Wno-format  # Suppress format string warnings
    )
endif()

# ============================================================================
# NOTES:
# ============================================================================
# 1. whisper.cpp will automatically build ggml as a dependency
# 2. Model files should be in GGML format (not GGUF)
# 3. Test with a small model first (tiny, base) before using larger models
# 4. Consider using quantized models for better performance on mobile
# ============================================================================
