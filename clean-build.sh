#!/bin/bash
# Clean build script to fix JDK image transformation errors

echo "Cleaning build artifacts and caches..."

# Clean local build directories
rm -rf .gradle
rm -rf build
rm -rf app/build

# Clean Gradle transform cache (this is often the culprit)
echo "Cleaning Gradle transform cache..."
rm -rf ~/.gradle/caches/transforms-3

echo ""
echo "Clean complete. Try building again in Android Studio."
echo ""
echo "If issues persist:"
echo "  1. File > Project Structure > SDK Location"
echo "     - Set 'JDK location' to JDK 17 (not JDK 21)"
echo "     - Android Studio's embedded JDK 17 should work"
echo "  2. File > Invalidate Caches / Restart"
echo "  3. File > Sync Project with Gradle Files"
echo "  4. Build > Clean Project"
echo "  5. Build > Rebuild Project"

