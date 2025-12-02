#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <memory>

// ============================================================================
// WHISPER.CPP INTEGRATION
// ============================================================================
// whisper.cpp is integrated via CMakeLists.txt
// ============================================================================

#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Initialize Whisper with model file
JNIEXPORT jlong JNICALL
Java_com_sttapp_stt_WhisperNative_initContext(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        LOGE("Failed to get model path");
        return 0;
    }
    
    LOGI("Initializing Whisper with model: %s", path);
    
    // Initialize whisper.cpp context with default parameters
    whisper_context_params cparams = whisper_context_default_params();
    // For Android, we typically don't use GPU, but you can enable it if your device supports it
    cparams.use_gpu = false; // Set to true if you have GPU support configured
    
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    if (ctx == nullptr) {
        LOGE("Failed to initialize Whisper context");
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

// Process audio data
JNIEXPORT jstring JNICALL
Java_com_sttapp_stt_WhisperNative_processAudio(JNIEnv *env, jobject thiz, jlong contextPtr, jshortArray audioData, jint sampleRate) {
    if (contextPtr == 0) {
        LOGE("Invalid context pointer");
        return env->NewStringUTF("");
    }
    
    jsize len = env->GetArrayLength(audioData);
    jshort *audio = env->GetShortArrayElements(audioData, nullptr);
    
    if (audio == nullptr) {
        LOGE("Failed to get audio data");
        return env->NewStringUTF("");
    }
    
    LOGI("Processing audio: %d samples at %d Hz", len, sampleRate);
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
    
    // Convert int16 PCM to float32
    std::vector<float> pcmf32(len);
    for (int i = 0; i < len; i++) {
        pcmf32[i] = audio[i] / 32768.0f;
    }
    
    // Run whisper inference
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.translate = false;
    params.language = nullptr; // auto-detect language
    params.n_threads = 4; // Adjust based on device capabilities
    
    if (whisper_full(ctx, params, pcmf32.data(), pcmf32.size()) != 0) {
        LOGE("Failed to process audio");
        env->ReleaseShortArrayElements(audioData, audio, JNI_ABORT);
        return env->NewStringUTF("");
    }
    
    // Extract text from segments
    std::string result;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        result += text;
        if (i < n_segments - 1) {
            result += " ";
        }
    }
    
    env->ReleaseShortArrayElements(audioData, audio, JNI_ABORT);
    return env->NewStringUTF(result.c_str());
}

// Free Whisper context
JNIEXPORT void JNICALL
Java_com_sttapp_stt_WhisperNative_freeContext(JNIEnv *env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) {
        return;
    }
    
    LOGI("Freeing Whisper context");
    
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
    whisper_free(ctx);
}

// Check if model is loaded
JNIEXPORT jboolean JNICALL
Java_com_sttapp_stt_WhisperNative_isModelLoaded(JNIEnv *env, jobject thiz, jlong contextPtr) {
    return contextPtr != 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
