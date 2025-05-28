#!/bin/bash

# Weather Widget Release Helper Script
# Usage: ./create-release.sh <version> [release-notes]
# Example: ./create-release.sh 3.2 "Added new weather icons and bug fixes"

set -e

# Check if version is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <version> [release-notes]"
    echo "Example: $0 3.2 'Added new weather icons and bug fixes'"
    exit 1
fi

VERSION="$1"
RELEASE_NOTES="${2:-Bug fixes and improvements}"
TAG="v$VERSION"

echo "🚀 Creating release for Weather Widget v$VERSION"
echo "📝 Release notes: $RELEASE_NOTES"
echo ""

# Check if we're in the right directory
if [ ! -f "app/build.gradle" ]; then
    echo "❌ Error: app/build.gradle not found. Are you in the project root?"
    exit 1
fi

# Check if git working directory is clean
if ! git diff-index --quiet HEAD --; then
    echo "❌ Error: Git working directory is not clean. Please commit your changes first."
    exit 1
fi

# Extract current version info
CURRENT_VERSION_CODE=$(grep "versionCode" app/build.gradle | grep -o '[0-9]\+')
CURRENT_VERSION_NAME=$(grep "versionName" app/build.gradle | sed 's/.*"\(.*\)".*/\1/')

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "📊 Version info:"
echo "   Current: v$CURRENT_VERSION_NAME (code: $CURRENT_VERSION_CODE)"
echo "   New:     v$VERSION (code: $NEW_VERSION_CODE)"
echo ""

# Update version in build.gradle
echo "📝 Updating app/build.gradle..."
sed -i.bak "s/versionCode = $CURRENT_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" app/build.gradle
sed -i.bak "s/versionName = \"$CURRENT_VERSION_NAME\"/versionName = \"$VERSION\"/" app/build.gradle
rm app/build.gradle.bak

# Commit version bump
echo "💾 Committing version bump..."
git add app/build.gradle
git commit -m "Bump version to $VERSION"

# Create and push tag
echo "🏷️  Creating tag $TAG..."
git tag "$TAG"

echo "📤 Pushing to GitHub..."
git push origin main
git push origin "$TAG"

echo ""
echo "✅ Release preparation complete!"
echo ""
echo "🌐 Next steps:"
echo "   1. Go to: https://github.com/jhalaga/weather-widget/releases/new"
echo "   2. Tag: $TAG (should be pre-filled)"
echo "   3. Title: Weather Widget v$VERSION"
echo "   4. Description: $RELEASE_NOTES"
echo "   5. Click 'Publish release'"
echo ""
echo "🔨 The GitHub Actions workflow will automatically build and attach the APK!"

# Check if gh CLI is available
if command -v gh &> /dev/null; then
    echo ""
    echo "🤖 GitHub CLI detected! You can also create the release directly:"
    echo "   gh release create $TAG --title 'Weather Widget v$VERSION' --notes '$RELEASE_NOTES'"
fi 