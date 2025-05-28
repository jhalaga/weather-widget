# APK Signing Guide

This document explains how APK signing works for the Weather Widget app and how to set up proper release signing.

## Current Setup

The app is currently configured to use **debug keystore signing** for release builds. This means:

✅ **Pros:**
- APKs can be installed on any device
- No keystore management needed
- Works immediately in CI/CD

⚠️ **Cons:**
- Not suitable for Play Store distribution
- Less secure than proper release signing
- All builds share the same debug signature

## APK Signing Explained

Android requires all APKs to be signed before installation:

- **Debug builds**: Automatically signed with debug keystore (`~/.android/debug.keystore`)
- **Release builds**: Need explicit signing configuration

## Current Configuration

In `app/build.gradle`, the release build type uses:

```gradle
signingConfigs {
    release {
        storeFile file("${System.getProperty('user.home')}/.android/debug.keystore")
        storePassword "android"
        keyAlias "androiddebugkey" 
        keyPassword "android"
    }
}
```

This uses the debug keystore for release builds, making them installable but not Play Store ready.

## Creating a Proper Release Keystore

For production releases, create a dedicated release keystore:

### 1. Generate Release Keystore

```bash
keytool -genkey -v -keystore weather-widget-release.keystore -alias weather-widget -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for:
- Keystore password (save this securely!)
- Key password (can be same as keystore password)
- Your name and organization details

### 2. Update build.gradle

```gradle
signingConfigs {
    release {
        if (project.hasProperty('RELEASE_STORE_FILE')) {
            storeFile file(RELEASE_STORE_FILE)
            storePassword RELEASE_STORE_PASSWORD
            keyAlias RELEASE_KEY_ALIAS
            keyPassword RELEASE_KEY_PASSWORD
        } else {
            // Fallback to debug keystore for CI/CD
            storeFile file("${System.getProperty('user.home')}/.android/debug.keystore")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }
    }
}
```

### 3. Create gradle.properties (locally)

Create `gradle.properties` in your project root:

```properties
RELEASE_STORE_FILE=path/to/weather-widget-release.keystore
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=weather-widget
RELEASE_KEY_PASSWORD=your_key_password
```

**⚠️ NEVER commit this file to git!** Add it to `.gitignore`.

### 4. For GitHub Actions

Add these as repository secrets in GitHub:
- `RELEASE_STORE_FILE_BASE64` (base64 encoded keystore file)
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then update the workflow to decode and use the keystore.

## Play Store Distribution

To distribute via Google Play Store:

1. Create a proper release keystore (as above)
2. Build a signed release APK
3. Upload to Play Console
4. The same keystore must be used for all future updates

## Testing Installation

- **Debug APKs**: Can be installed alongside release versions
- **Release APKs**: Will replace any existing installation with same applicationId

## Security Notes

🔒 **Keystore Security:**
- Keep your release keystore file secure and backed up
- Never share keystore passwords
- Use strong passwords for production keystores
- Consider using Android App Bundle (.aab) for Play Store

## Current Status

✅ **Current**: Debug-signed release APKs (installable, not Play Store ready)
🎯 **Future**: Proper release keystore for Play Store distribution

The current setup works perfectly for direct APK distribution via GitHub releases! 