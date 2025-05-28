@echo off
echo Building Weather Widget Release APK...
echo.
echo Requirements: Java 17+ and Android SDK
echo.

REM Clean and build release APK
echo [1/3] Cleaning previous builds...
call gradlew clean

echo.
echo [2/3] Building release APK...
call gradlew assembleRelease

REM Check if build was successful
if not exist "app\build\outputs\apk\release\app-release-unsigned.apk" (
    echo ERROR: Build failed! APK not found.
    pause
    exit /b 1
)

echo.
echo [3/3] Copying APK to release directory...

REM Create releases directory if it doesn't exist
if not exist "releases" mkdir releases

REM Copy and rename APK with version
copy "app\build\outputs\apk\release\app-release-unsigned.apk" "releases\weather-widget-v3.0.apk"

echo.
echo ✅ SUCCESS! APK created at: releases\weather-widget-v3.0.apk
echo.
echo File size:
dir "releases\weather-widget-v3.0.apk" | findstr weather-widget

echo.
echo You can now:
echo 1. Test the APK on your device: adb install releases\weather-widget-v3.0.apk
echo 2. Upload to GitHub releases manually
echo 3. Use GitHub Actions for automated releases
echo.
pause 