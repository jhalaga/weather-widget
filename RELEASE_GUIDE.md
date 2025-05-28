# 🚀 Weather Widget Release Guide

**Simple step-by-step instructions for releasing your app. No need to remember complex steps!**

## 📋 Quick Release Checklist

- [ ] Code is tested and working locally
- [ ] Version numbers updated (if needed)
- [ ] Choose release method below

---

## 🎯 Method 1: Automated GitHub Release (Recommended)

**Creates a release with downloadable APK automatically**

### Step 1: Create Release on GitHub
1. Go to your repository: `https://github.com/jhalaga/weather-widget`
2. Click **"Releases"** → **"Create a new release"**
3. Fill in:
   - **Tag**: `v3.0` (or next version)
   - **Title**: `Weather Widget v3.0 - Real Weather Integration ✨`
   - **Description**: Copy from README changelog section
4. Click **"Publish release"**

### Step 2: Wait for APK
- GitHub Actions will automatically build the APK (takes ~3-5 minutes)
- APK will be attached as `weather-widget-v3.0.apk`
- Users can download directly from the release page

**✅ Done! Your users can now download the APK from the release page.**

---

## 🎯 Method 2: Manual Test Build

**Test the build process or get APK for manual upload**

### Step 1: Trigger Manual Build
1. Go to repository → **"Actions"** tab
2. Click **"Build and Release APK"** (left sidebar)
3. Click **"Run workflow"** button
4. Enter version: `v3.0`
5. Click **"Run workflow"**

### Step 2: Download APK
- Wait for build to complete (~3-5 minutes)
- Click on the completed workflow run
- Download APK from "Artifacts" section
- Use this APK for testing or manual upload

---

## 🎯 Method 3: Local Build

**Build APK on your computer**

### Windows:
```cmd
.\release-local.bat
```

### Linux/Mac:
```bash
./release-local.sh
```

**APK Location:** `releases/weather-widget-v3.0.apk`

---

## 🔄 For Future Versions

### Update Version Numbers:
1. Edit `app/build.gradle`:
   ```gradle
   versionCode = 5        // Increment by 1
   versionName = "3.1"    // Your new version
   ```

2. Update release scripts (change `v3.0` to `v3.1`):
   - `release-local.bat`
   - `release-local.sh`

3. Follow any release method above with new version number

---

## ⚙️ System Requirements

- **GitHub Actions**: No setup needed (works automatically)
- **Local Building**: Java 17+ and Android SDK

---

## 🔧 Configuration Notes

### Java Version Management
The project is configured to work automatically:
- **GitHub Actions**: Uses Java 17 from CI environment
- **Local Development**: Uses your system Java (any version 17+)

### If Local Build Fails:
1. Check you have Java 17+ installed: `java -version`
2. If you need a specific Java version locally, edit `gradle.properties`:
   ```properties
   # Uncomment and set your Java path:
   org.gradle.java.home=C:\\Program Files\\Your\\Java\\Path
   ```
3. **Important**: Keep this commented out for GitHub Actions to work!

---

## 🐛 Troubleshooting

### GitHub Actions Build Fails:
- Check the Actions tab for error details
- Usually fixes itself on retry
- Contact support if persists

### Local Build Issues:
- Run: `./gradlew clean assembleRelease`
- Check Java version: `java -version`
- Ensure Android SDK is installed

### APK Not Found:
- Check `app/build/outputs/apk/release/` directory
- Run build command again
- Try different release method

---

## 📊 Release Analytics

Track your releases:
- **Downloads**: GitHub repository → Insights → Traffic
- **Issues**: Monitor the Issues tab for user feedback
- **Usage**: Consider adding analytics to future versions

---

## 🎉 Success!

Once released, users can:
1. Go to your **Releases** page
2. Download `weather-widget-vX.X.apk`
3. Install on their Android device
4. Enjoy your weather widget!

**The process is now fully automated and documented. Just follow the steps above for any future releases!** ✨ 