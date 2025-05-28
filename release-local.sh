#!/bin/bash

echo "Building Weather Widget Release APK..."
echo
echo "Requirements: Java 17+ and Android SDK"
echo

# Clean and build release APK
echo "[1/3] Cleaning previous builds..."
./gradlew clean

echo
echo "[2/3] Building release APK..."
./gradlew assembleRelease

# Check if build was successful
if [ ! -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    echo "ERROR: Build failed! APK not found."
    exit 1
fi

echo
echo "[3/3] Copying APK to release directory..."

# Create releases directory if it doesn't exist
mkdir -p releases

# Copy and rename APK with version
cp "app/build/outputs/apk/release/app-release-unsigned.apk" "releases/weather-widget-v3.0.apk"

echo
echo "✅ SUCCESS! APK created at: releases/weather-widget-v3.0.apk"
echo

# Show file size
ls -lh "releases/weather-widget-v3.0.apk"

echo
echo "You can now:"
echo "1. Test the APK on your device: adb install releases/weather-widget-v3.0.apk"
echo "2. Upload to GitHub releases manually"
echo "3. Use GitHub Actions for automated releases"
echo 