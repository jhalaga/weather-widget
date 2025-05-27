package com.jozefhalaga.weatherwidget

import android.Manifest
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.widget.EditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private val LOCATION_REQUEST = 42

    private lateinit var currentLocationText: TextView
    private lateinit var locationRadioGroup: RadioGroup
    private lateinit var radioAutoLocation: RadioButton
    private lateinit var radioCustomLocation: RadioButton
    private lateinit var customLocationCard: LinearLayout
    private lateinit var citySearchEditText: EditText
    private lateinit var searchCityButton: TextView
    private lateinit var searchResultsLabel: TextView
    private lateinit var searchResultsChipGroup: ChipGroup
    private lateinit var selectedLocationText: TextView
    private lateinit var statusText: TextView
    private lateinit var helpIcon: TextView

    private var currentLocationData: LocationData? = null
    private var selectedCustomLocation: LocationData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            initViews()
            setupEventListeners()
            loadCurrentSettings()
            
            // Automatically detect location if GPS is selected after loading settings
            if (radioAutoLocation.isChecked) {
                currentLocationText.text = "Detecting location..."
                requestLocationSafelyWithRetry()
            } else {
                // Check permissions quietly for custom location mode
                checkPermissionsQuietly()
            }
            
        } catch (e: Exception) {
            // If there's an error, create a simple fallback layout
            val textView = TextView(this)
            textView.text = "Error initializing app: ${e.message}"
            textView.textSize = 16f
            textView.setPadding(32, 32, 32, 32)
            setContentView(textView)
        }
    }

    private fun initViews() {
        currentLocationText = findViewById(R.id.currentLocationText)
        locationRadioGroup = findViewById(R.id.locationRadioGroup)
        radioAutoLocation = findViewById(R.id.radioAutoLocation)
        radioCustomLocation = findViewById(R.id.radioCustomLocation)
        customLocationCard = findViewById(R.id.customLocationCard)
        citySearchEditText = findViewById(R.id.citySearchEditText)
        searchCityButton = findViewById(R.id.searchCityButton)
        searchResultsLabel = findViewById(R.id.searchResultsLabel)
        searchResultsChipGroup = findViewById(R.id.searchResultsChipGroup)
        selectedLocationText = findViewById(R.id.selectedLocationText)
        statusText = findViewById(R.id.statusText)
        helpIcon = findViewById(R.id.helpIcon)
        
        currentLocationText.text = "Detecting location..."
    }

    private fun clearSearchResults() {
        try {
            searchResultsChipGroup.removeAllViews()
            searchResultsLabel.visibility = View.GONE
            selectedLocationText.visibility = View.GONE
        } catch (e: Exception) {
            showStatus("Error clearing search results: ${e.message}")
        }
    }

    private fun selectLocation(location: LocationData) {
        selectedCustomLocation = location
        selectedLocationText.text = "${location.city}\nLat: ${String.format("%.4f", location.latitude)}, Lon: ${String.format("%.4f", location.longitude)}"
        selectedLocationText.visibility = View.VISIBLE
        citySearchEditText.setText(location.city)
        
        // Auto-save custom location immediately
        WeatherLocationManager.saveLocationPreference(this, true, location)
        currentLocationData = location
        updateLocationDisplay(location)
        // Cache the custom location for widget use
        WeatherLocationManager.cacheLocationData(this, location)
        // Update widgets automatically
        updateAllWidgets()
        
        showStatus("Location saved: ${location.city}", 3000)
    }

    private fun setupEventListeners() {
        setupRadioGroupListener()

        helpIcon.setOnClickListener {
            showWeatherIconsDialog()
        }

        searchCityButton.setOnClickListener {
            searchForCity()
        }

        citySearchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchForCity()
                true
            } else {
                false
            }
        }

        // Clear search results when text is cleared
        citySearchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    clearSearchResults()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupRadioGroupListener() {
        locationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAutoLocation -> {
                    customLocationCard.visibility = View.GONE
                    clearSearchResults()
                    showStatus("GPS location selected - detecting location...")
                    currentLocationText.text = "Detecting location..."
                    
                    // Auto-save GPS preference immediately
                    WeatherLocationManager.saveLocationPreference(this, false)
                    
                    // Start location detection with improved reliability
                    requestLocationSafelyWithRetry()
                }
                R.id.radioCustomLocation -> {
                    customLocationCard.visibility = View.VISIBLE
                    showStatus("Select a custom location below")
                }
            }
        }
    }

    private fun searchForCity() {
        val query = citySearchEditText.text.toString().trim()
        if (query.length < 2) {
            showStatus("Please enter at least 2 characters to search")
            return
        }

        showStatus("Searching for '$query'...")
        lifecycleScope.launch {
            try {
                val results = WeatherLocationManager.searchCities(query)
                if (results.isNotEmpty()) {
                    showSearchResults(results)
                } else {
                    showStatus("No cities found for '$query'. Try a different search term.")
                }
            } catch (e: Exception) {
                showStatus("Search error: ${e.message}")
            }
        }
    }

    private fun showSearchResults(results: List<SearchResult>) {
        try {
            // Clear previous results and show search results
            searchResultsChipGroup.removeAllViews()
            searchResultsLabel.visibility = View.VISIBLE
            
            results.forEach { result ->
                val chip = Chip(this)
                chip.text = result.displayName
                chip.isCheckable = true
                chip.setOnClickListener {
                    val location = LocationData(
                        result.latitude, 
                        result.longitude, 
                        result.displayName, 
                        true
                    )
                    selectLocation(location)
                    // Uncheck other chips
                    for (i in 0 until searchResultsChipGroup.childCount) {
                        val otherChip = searchResultsChipGroup.getChildAt(i) as Chip
                        otherChip.isChecked = otherChip == chip
                    }
                }
                searchResultsChipGroup.addView(chip)
            }
            
            showStatus("Found ${results.size} locations. Tap one to select it.")
        } catch (e: Exception) {
            showStatus("Error displaying search results: ${e.message}")
        }
    }

    private fun showWeatherIconsDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weather_icons, null)
            val closeButton = dialogView.findViewById<TextView>(R.id.closeButton)
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()
            
            closeButton.setOnClickListener {
                dialog.dismiss()
            }
            
            dialog.show()
        } catch (e: Exception) {
            showStatus("Error showing weather icons dialog: ${e.message}")
        }
    }

    private fun loadCurrentSettings() {
        try {
            // Temporarily disable the radio group listener to prevent unwanted triggers
            locationRadioGroup.setOnCheckedChangeListener(null)
            
            val isUsingCustom = WeatherLocationManager.isUsingCustomLocation(this)
            
            if (isUsingCustom) {
                radioCustomLocation.isChecked = true
                customLocationCard.visibility = View.VISIBLE
                
                val customLocation = WeatherLocationManager.getCustomLocation(this)
                if (customLocation != null) {
                    selectedCustomLocation = customLocation
                    selectLocation(customLocation)
                    currentLocationData = customLocation
                    updateLocationDisplay(customLocation)
                    // Cache the custom location for widget use
                    WeatherLocationManager.cacheLocationData(this, customLocation)
                }
            } else {
                radioAutoLocation.isChecked = true
                customLocationCard.visibility = View.GONE
                // Location will be detected automatically in onCreate
            }
            
            // Re-enable the radio group listener after loading settings
            setupRadioGroupListener()
            
        } catch (e: Exception) {
            showStatus("Error loading settings: ${e.message}")
            // Set safe defaults
            radioAutoLocation.isChecked = true
            customLocationCard.visibility = View.GONE
            // Re-enable the listener even in error case
            setupRadioGroupListener()
        }
    }

    private fun checkPermissionsQuietly() {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasLocationPermission) {
            showStatus("Location permission available", 2000)
        } else {
            showStatus("Location permission not granted. Select GPS option to grant permission.", 3000)
        }
    }

    private fun requestLocationSafely() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showStatus("Requesting location permission...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_REQUEST
                )
            } else {
                loadCurrentLocationSafely()
            }
        } catch (e: Exception) {
            showStatus("Error requesting location: ${e.message}")
            // Even if there's an error, try to load fallback location
            loadCurrentLocationSafely()
        }
    }

    private fun requestLocationSafelyWithRetry() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showStatus("Requesting location permission...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_REQUEST
                )
            } else {
                loadCurrentLocationWithRetry()
            }
        } catch (e: Exception) {
            showStatus("Error requesting location: ${e.message}")
            // Even if there's an error, try to load fallback location
            loadCurrentLocationWithRetry()
        }
    }

    private fun loadCurrentLocationSafely() {
        showStatus("Getting your location...")
        currentLocationText.text = "Detecting location..."
        
        lifecycleScope.launch {
            try {
                val location = WeatherLocationManager.getCurrentLocation(this@MainActivity)
                currentLocationData = location
                updateLocationDisplay(location)
                // Cache the location for widget use
                WeatherLocationManager.cacheLocationData(this@MainActivity, location)
                
                // Provide feedback based on location type
                val feedback = if (location.city.contains("Fallback") || location.city.contains("London")) {
                    "Location detected using fallback service"
                } else {
                    "Location detected: ${location.city}"
                }
                showStatus(feedback, 2000)
                
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("timeout") == true -> "Location detection timed out"
                    e.message?.contains("permission") == true -> "Location permission required"
                    e.message?.contains("disabled") == true -> "Location services disabled"
                    else -> "Could not detect location"
                }
                
                showStatus("$errorMessage - using fallback", 3000)
                currentLocationText.text = "Using fallback location"
                
                // Try to get a fallback location
                try {
                    val fallback = LocationData(51.5074, -0.1278, "London (Fallback)")
                    currentLocationData = fallback
                    updateLocationDisplay(fallback)
                    // Cache the fallback location for widget use
                    WeatherLocationManager.cacheLocationData(this@MainActivity, fallback)
                } catch (fallbackError: Exception) {
                    currentLocationText.text = "Unable to determine location"
                    showStatus("Location detection failed completely", 3000)
                }
            }
        }
    }

    private fun loadCurrentLocationWithRetry() {
        showStatus("Getting your location...")
        currentLocationText.text = "Detecting location..."
        
        lifecycleScope.launch {
            var attempt = 1
            val maxAttempts = 2
            
            while (attempt <= maxAttempts) {
                try {
                    showStatus("Detecting location... (attempt $attempt/$maxAttempts)")
                    
                    val location = WeatherLocationManager.getCurrentLocation(this@MainActivity)
                    currentLocationData = location
                    updateLocationDisplay(location)
                    // Cache the location for widget use
                    WeatherLocationManager.cacheLocationData(this@MainActivity, location)
                    
                    // Update widgets automatically after successful location detection
                    updateAllWidgets()
                    
                    // Provide feedback based on location type
                    val feedback = if (location.city.contains("Fallback") || location.city.contains("London")) {
                        "Location detected using fallback service"
                    } else {
                        "Location detected: ${location.city}"
                    }
                    showStatus(feedback, 3000)
                    return@launch // Success - exit the function
                    
                } catch (e: Exception) {
                    if (attempt == maxAttempts) {
                        // Final attempt failed
                        val errorMessage = when {
                            e.message?.contains("timeout") == true -> "Location detection timed out"
                            e.message?.contains("permission") == true -> "Location permission required"
                            e.message?.contains("disabled") == true -> "Location services disabled"
                            else -> "Could not detect location"
                        }
                        
                        showStatus("$errorMessage - using fallback", 3000)
                        currentLocationText.text = "Using fallback location"
                        
                        // Try to get a fallback location
                        try {
                            val fallback = LocationData(51.5074, -0.1278, "London (Fallback)")
                            currentLocationData = fallback
                            updateLocationDisplay(fallback)
                            // Cache the fallback location for widget use
                            WeatherLocationManager.cacheLocationData(this@MainActivity, fallback)
                            updateAllWidgets()
                        } catch (fallbackError: Exception) {
                            currentLocationText.text = "Unable to determine location"
                            showStatus("Location detection failed completely", 3000)
                        }
                    } else {
                        // Not the final attempt, try again
                        showStatus("Retrying location detection...", 1000)
                        delay(1500) // Wait before retry
                        attempt++
                    }
                }
            }
        }
    }

    private fun updateLocationDisplay(location: LocationData) {
        try {
            val locationStr = if (location.isCustom) {
                "${location.city}\n(Custom Location)"
            } else {
                "${location.city}\nLat: ${String.format("%.4f", location.latitude)}, Lon: ${String.format("%.4f", location.longitude)}"
            }
            currentLocationText.text = locationStr
        } catch (e: Exception) {
            currentLocationText.text = "Error displaying location"
        }
    }



    private fun updateAllWidgets() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetComponent = ComponentName(this, HelloWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            if (widgetIds.isNotEmpty()) {
                // Trigger widget update
                val updateIntent = Intent(this, HelloWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                sendBroadcast(updateIntent)
                
                showStatus("Weather widgets updated with new location!", 2000)
            } else {
                showStatus("No weather widgets found to update.", 2000)
            }
        } catch (e: Exception) {
            showStatus("Error updating widgets: ${e.message}")
        }
    }

    private fun showStatus(message: String, hideAfterMs: Long = 0) {
        try {
            statusText.text = message
            statusText.visibility = View.VISIBLE
            
            if (hideAfterMs > 0) {
                statusText.postDelayed({
                    statusText.visibility = View.GONE
                }, hideAfterMs)
            }
        } catch (e: Exception) {
            // If we can't even show status, there's a serious problem
            // but don't crash the app
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            if (requestCode == LOCATION_REQUEST) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadCurrentLocationWithRetry()
                } else {
                    showStatus("Location permission denied. Using fallback location.", 3000)
                    // Load fallback location
                    try {
                        val fallback = LocationData(51.5074, -0.1278, "London (Fallback)")
                        currentLocationData = fallback
                        updateLocationDisplay(fallback)
                        // Cache the fallback location for widget use
                        WeatherLocationManager.cacheLocationData(this, fallback)
                        updateAllWidgets()
                    } catch (e: Exception) {
                        currentLocationText.text = "Unable to determine location"
                    }
                }
            }
        } catch (e: Exception) {
            showStatus("Error handling permission result: ${e.message}")
        }
    }
}