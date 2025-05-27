# 🚀 Release Guide for Weather Widget

This guide will help you create releases for your Weather Widget app so users can download APK files.

## 📋 Prerequisites

- Your code is committed and pushed to GitHub
- You have a GitHub repository set up
- Android Studio or command line with Gradle

## 🏗️ Option 1: Automated GitHub Releases (Recommended)

### Step 1: Push Your Code
```bash
git add .
git commit -m "Prepare for v3.0 release"
git push origin main
```

### Step 2: Create a Release on GitHub
1. Go to your GitHub repository
2. Click **"Releases"** (on the right side or in the navigation)
3. Click **"Create a new release"**
4. Fill in the release details:
   - **Tag version**: `v3.0` (must start with 'v')
   - **Release title**: `Weather Widget v3.0 - Real Weather Integration ✨`
   - **Description**: Copy from your CHANGELOG in README.md

### Step 3: Automatic APK Generation
The GitHub Actions workflow will automatically:
- Build the release APK
- Attach it to the release as `weather-widget-v3.0.apk`
- Make it available for download

## 🛠️ Option 2: Manual Release Process

### Step 1: Build Release APK Locally

**On Windows:**
```cmd
.\release-local.bat
```

**On Linux/Mac:**
```bash
./release-local.sh
```

**Or manually:**
```bash
./gradlew clean assembleRelease
mkdir -p releases
cp app/build/outputs/apk/release/app-release-unsigned.apk releases/weather-widget-v3.0.apk
```

### Step 2: Create GitHub Release
1. Go to GitHub → Your Repository → Releases
2. Click "Create a new release"
3. Set tag: `v3.0`
4. Upload the APK file from `releases/weather-widget-v3.0.apk`

## 📱 Testing Your Release

Before publishing, test the APK:

```bash
# Install on connected Android device
adb install releases/weather-widget-v3.0.apk

# Or install over existing version
adb install -r releases/weather-widget-v3.0.apk
```

## 🔄 For Future Releases

### Update Version Numbers
1. Edit `app/build.gradle`:
   ```gradle
   versionCode = 5        // Increment by 1
   versionName = "3.1"    // Your new version
   ```

2. Update release scripts:
   - `release-local.bat`: Change `v3.0` to `v3.1`
   - `release-local.sh`: Change `v3.0` to `v3.1`

3. Update README.md changelog section

### Create New Release
Follow the same process with the new version number.

## 🎯 Release Checklist

- [ ] Code is tested and working
- [ ] Version numbers updated in `build.gradle`
- [ ] README.md updated with new features
- [ ] APK builds successfully locally
- [ ] APK tested on Android device
- [ ] Release created on GitHub
- [ ] APK attached to GitHub release
- [ ] Release notes written

## 🔧 Troubleshooting

### Build Fails with Lint Errors
The project is configured to skip lint checks for releases. If you still get errors:
```bash
./gradlew assembleRelease --stacktrace
```

### GitHub Actions Not Triggering
- Ensure the tag starts with 'v' (e.g., `v3.0`)
- Check that `.github/workflows/release.yml` exists
- Check the Actions tab for error messages

### APK Not Signed
The current setup uses debug signing. For production:
1. Create a release keystore
2. Add signing configuration to `build.gradle`
3. Store keystore credentials securely

## 📊 Release Analytics

After releasing, you can track:
- Download counts (GitHub Insights → Traffic)
- User feedback (GitHub Issues)
- Crash reports (if you add crash reporting)

---

🎉 **Your Weather Widget is now ready for users to download!**

The README.md instructions will work once you create your first release following this guide. 