# Weather Widget Release Helper Script (PowerShell)
# Usage: .\create-release.ps1 <version> [release-notes]
# Example: .\create-release.ps1 3.2 "Added new weather icons and bug fixes"

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,
    
    [Parameter(Mandatory=$false)]
    [string]$ReleaseNotes = "Bug fixes and improvements"
)

$ErrorActionPreference = "Stop"

$Tag = "v$Version"

Write-Host "🚀 Creating release for Weather Widget v$Version" -ForegroundColor Green
Write-Host "📝 Release notes: $ReleaseNotes" -ForegroundColor Yellow
Write-Host ""

# Check if we're in the right directory
if (-not (Test-Path "app\build.gradle")) {
    Write-Host "❌ Error: app\build.gradle not found. Are you in the project root?" -ForegroundColor Red
    exit 1
}

# Check if git working directory is clean
$gitStatus = git status --porcelain
if ($gitStatus) {
    Write-Host "❌ Error: Git working directory is not clean. Please commit your changes first." -ForegroundColor Red
    exit 1
}

# Extract current version info
$buildGradleContent = Get-Content "app\build.gradle"
$currentVersionCode = ($buildGradleContent | Select-String "versionCode = (\d+)").Matches[0].Groups[1].Value
$currentVersionName = ($buildGradleContent | Select-String 'versionName = "([^"]+)"').Matches[0].Groups[1].Value

$newVersionCode = [int]$currentVersionCode + 1

Write-Host "📊 Version info:" -ForegroundColor Cyan
Write-Host "   Current: v$currentVersionName (code: $currentVersionCode)"
Write-Host "   New:     v$Version (code: $newVersionCode)"
Write-Host ""

# Update version in build.gradle
Write-Host "📝 Updating app\build.gradle..." -ForegroundColor Yellow
$buildGradleContent = $buildGradleContent -replace "versionCode = $currentVersionCode", "versionCode = $newVersionCode"
$buildGradleContent = $buildGradleContent -replace "versionName = `"$currentVersionName`"", "versionName = `"$Version`""
$buildGradleContent | Set-Content "app\build.gradle"

# Commit version bump
Write-Host "💾 Committing version bump..." -ForegroundColor Yellow
git add app\build.gradle
git commit -m "Bump version to $Version"

# Create and push tag
Write-Host "🏷️ Creating tag $Tag..." -ForegroundColor Yellow
git tag $Tag

Write-Host "📤 Pushing to GitHub..." -ForegroundColor Yellow
git push origin main
git push origin $Tag

Write-Host ""
Write-Host "✅ Release preparation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "🌐 Next steps:" -ForegroundColor Cyan
Write-Host "   1. Go to: https://github.com/jhalaga/weather-widget/releases/new"
Write-Host "   2. Tag: $Tag (should be pre-filled)"
Write-Host "   3. Title: Weather Widget v$Version"
Write-Host "   4. Description: $ReleaseNotes"
Write-Host "   5. Click 'Publish release'"
Write-Host ""
Write-Host "🔨 The GitHub Actions workflow will automatically build and attach the APK!" -ForegroundColor Green

# Check if gh CLI is available
if (Get-Command gh -ErrorAction SilentlyContinue) {
    Write-Host ""
    Write-Host "🤖 GitHub CLI detected! You can also create the release directly:" -ForegroundColor Magenta
    Write-Host "   gh release create $Tag --title 'Weather Widget v$Version' --notes '$ReleaseNotes'"
} 