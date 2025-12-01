#!/bin/bash
# Build script for Android STT App

echo "Building Android STT App APK..."

# Check if gradlew exists, if not, create it
if [ ! -f "gradlew" ]; then
    echo "Gradle wrapper not found. Please run 'gradle wrapper' first, or use Android Studio to generate it."
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

# Build the APK
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Build successful!"
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on your device:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo ""
    echo "✗ Build failed. Please check the error messages above."
    exit 1
fi

