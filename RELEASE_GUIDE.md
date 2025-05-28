# 🚀 Weather Widget Release Guide

**Simple step-by-step instructions for releasing your app. No need to remember complex steps!**

## 📋 Quick Release Checklist

- [ ] Code is tested and working locally
- [ ] Version numbers updated (if needed)
- [ ] Choose release method below

---

## Automated Release System

The project now has an automated release system that builds and publishes APK files whenever you create a GitHub release.

### How It Works

1. **GitHub Actions Workflow**: Located in `.github/workflows/release.yml`
2. **Triggers**: 
   - When you create a new GitHub release
   - Manual workflow dispatch (for testing)
3. **Output**: Automatically builds and attaches APK files to the release

## Creating a New Release

### Method 1: GitHub Web Interface (Recommended)

1. **Prepare the version**:
   - Update version in `app/build.gradle`:
     ```gradle
     versionCode = X  // Increment by 1
     versionName = "X.X"  // New version number
     ```
   - Commit and push changes:
     ```bash
     git add app/build.gradle
     git commit -m "Bump version to X.X"
     git push origin main
     ```

2. **Create the release**:
   - Go to your GitHub repository
   - Click "Releases" → "Create a new release"
   - Click "Choose a tag" → Type new tag (e.g., `v3.2`)
   - Set release title (e.g., "Weather Widget v3.2")
   - Add release notes describing changes
   - Click "Publish release"

3. **Automatic build**:
   - GitHub Actions will automatically trigger
   - APK will be built and attached to the release
   - Users can download from the Releases page

### Method 2: Command Line

1. **Prepare and tag**:
   ```bash
   # Update version in app/build.gradle first
   git add app/build.gradle
   git commit -m "Bump version to X.X"
   git tag vX.X
   git push origin main
   git push origin vX.X
   ```

2. **Create release via GitHub CLI** (if installed):
   ```bash
   gh release create vX.X --title "Weather Widget vX.X" --notes "Release notes here"
   ```

## Testing the Workflow

You can test the build process without creating a release:

1. Go to your repository on GitHub
2. Click "Actions" tab
3. Select "Build and Release APK" workflow
4. Click "Run workflow"
5. Enter a version tag (e.g., `v3.1-test`)
6. Click "Run workflow"

This will build the APK and upload it as an artifact (downloadable from the workflow run page).

## Troubleshooting

### Build Fails
- Check the Actions tab for error logs
- Common issues:
  - Java version conflicts (workflow uses Java 17)
  - Gradle daemon issues (workflow clears cache)
  - Missing dependencies

### APK Not Attached
- Ensure the release was created properly
- Check if the build completed successfully in Actions
- Verify the APK file was generated in the build logs

### Manual Local Build
If you need to build locally for testing:
```bash
./gradlew assembleRelease
```
The APK will be in `app/build/outputs/apk/release/`

## Release Checklist

Before creating a release:

- [ ] Test the app thoroughly
- [ ] Update version code and name in `app/build.gradle`
- [ ] Update README.md if needed
- [ ] Write release notes describing changes
- [ ] Commit and push all changes
- [ ] Create and push the version tag
- [ ] Create the GitHub release
- [ ] Verify the APK builds successfully
- [ ] Test download and installation of the APK

## Version Numbering

Follow semantic versioning:
- **Major.Minor.Patch** (e.g., 3.1.0)
- **versionCode**: Integer that increments with each release
- **versionName**: Human-readable version string

Example:
```gradle
versionCode = 5      // Incremented from previous
versionName = "3.1"  // Semantic version
```

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