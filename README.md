# Weather Widget for Android

A modern Android weather widget that displays a 16-day weather forecast directly on your home screen. The widget automatically detects your location or allows you to set a custom location, providing temperature forecasts using the Open-Meteo API.

## Features

- **16-Day Weather Forecast**: View daily maximum and minimum temperatures for the next 16 days
- **Weather Icons**: Visual weather condition indicators with 12 different icon types (sunny, cloudy, rain, snow, thunderstorm, etc.)
- **Automatic Location Detection**: Uses GPS and network location services to automatically detect your current location
- **Custom Location Search**: Search and select any city worldwide for weather forecasts
- **Smart Location Handling**: Intelligent city name display with support for international locations
- **Home Screen Widget**: Compact widget design that fits perfectly on your Android home screen
- **Offline Fallback**: Uses IP-based location detection when GPS is unavailable
- **Material Design**: Modern UI following Material Design guidelines

## Screenshots

*Screenshots coming soon - add your widget screenshots here*

## Installation

### Prerequisites

- Android device running Android 7.0 (API level 24) or higher
- Android Studio (for development)
- Java 21 or higher

### Building from Source

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/weather-widget.git
   cd weather-widget
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository folder

3. **Build the project**
   ```bash
   # For Windows
   .\gradlew.bat assembleDebug
   
   # For Linux/macOS
   ./gradlew assembleDebug
   ```

4. **Install on device**
   ```bash
   # Connect your Android device and enable USB debugging
   .\gradlew.bat installDebug
   ```

The APK will be created at: `app\build\outputs\apk\debug\app-debug.apk`

### Release Build

To build a release version:
```bash
.\gradlew.bat assembleRelease
```

## Usage

### Setting up the Widget

1. **Install the app** on your Android device
2. **Grant permissions**: The app will request location permissions for automatic location detection
3. **Add widget to home screen**:
   - Long press on your home screen
   - Select "Widgets"
   - Find "Weather Widget" and drag it to your home screen

### Configuring Location

The app offers two location modes:

#### Automatic Location (GPS)
- Select "Use GPS Location" in the app
- The widget will automatically use your current location
- Requires location permissions

#### Custom Location
- Select "Use Custom Location" in the app
- Search for any city using the search function
- Select your desired location from the search results
- The widget will use this fixed location

### App Interface

- **Location Settings**: Choose between GPS and custom location
- **City Search**: Type at least 2 characters to search for cities
- **Search Results**: Tap on search results to select a location
- **Save Settings**: Apply your location preferences
- **Update Widgets**: Manually refresh all widgets on your home screen
- **Weather Icons Legend**: Expandable help section showing all weather icon meanings

## Technical Details

### Architecture

- **Language**: Kotlin
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 35)
- **Architecture**: Single Activity with AppWidget Provider

### Key Components

- `MainActivity.kt`: Main configuration interface
- `HelloWidgetProvider.kt`: Widget provider handling weather data and display
- `LocationManager.kt`: Location detection and city search functionality

### APIs Used

- **Open-Meteo API**: Free weather forecast API (no API key required)
- **BigDataCloud API**: Reverse geocoding for location names
- **Geolocation-DB**: IP-based location fallback

### Dependencies

- AndroidX AppCompat
- Material Design Components
- Kotlin Coroutines
- AndroidX Lifecycle
- Core Library Desugaring (for Java 8+ features on older Android versions)

### Permissions

- `ACCESS_FINE_LOCATION`: For precise GPS location detection
- `ACCESS_COARSE_LOCATION`: For network-based location detection
- `INTERNET`: For weather data and location services

## Development

### Project Structure

```
app/src/main/
├── java/com/jozefhalaga/weatherwidget/
│   ├── MainActivity.kt              # Main app configuration
│   ├── HelloWidgetProvider.kt       # Widget implementation
│   └── LocationManager.kt           # Location services
├── res/
│   ├── layout/
│   │   ├── activity_main.xml        # Main app layout
│   │   └── hello_widget.xml         # Widget layout
│   └── values/                      # Strings, colors, styles
└── AndroidManifest.xml
```

### Building and Testing

```bash
# Clean project
.\gradlew.bat clean

# Run tests
.\gradlew.bat test

# Install debug version
.\gradlew.bat installDebug

# Generate release APK
.\gradlew.bat assembleRelease
```

### Widget Development Notes

- Widget updates are handled in `HelloWidgetProvider.onUpdate()`
- Location detection uses a fallback hierarchy: GPS → Network → IP-based
- Weather data is fetched from Open-Meteo API with 16-day forecasts including weather codes
- Widget layout supports up to 16 days of forecast data with weather icons
- Weather icons are mapped from WMO weather codes to 12 distinct visual categories

## ~~Contributing~~

~~Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.~~

### ~~Development Guidelines~~

~~1. Follow Kotlin coding conventions
2. Maintain compatibility with Android API 24+
3. Test on multiple device sizes and Android versions
4. Update documentation for new features~~

### ~~Reporting Issues~~

~~Please use the GitHub issue tracker to report bugs or request features. Include:~~
- ~~Android version and device model~~
- ~~Steps to reproduce the issue~~
- ~~Expected vs actual behavior~~
- ~~Screenshots if applicable~~

## License

This project is open source.

## Acknowledgments

- [Open-Meteo](https://open-meteo.com/) for providing free weather data
- [BigDataCloud](https://www.bigdatacloud.com/) for geocoding services
- [Material Design](https://material.io/) for design guidelines

## Changelog

### Version 2.1
- Added weather icons to widget display
- 12 different weather condition icons based on WMO weather codes
- Enhanced visual representation of weather conditions
- Added collapsible weather icons legend in main app
- Interactive help section for understanding weather symbols

### Version 2.0
- Improved location search functionality
- Enhanced city name display logic
- Better handling of international location names
- UI improvements and bug fixes

### Version 1.0
- Initial release
- Basic weather widget functionality
- GPS and custom location support


