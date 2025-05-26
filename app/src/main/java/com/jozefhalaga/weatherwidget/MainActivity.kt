package com.jozefhalaga.weatherwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val LOCATION_REQUEST = 42

    private lateinit var currentLocationText: TextView
    private lateinit var locationRadioGroup: RadioGroup
    private lateinit var radioAutoLocation: RadioButton
    private lateinit var radioCustomLocation: RadioButton
    private lateinit var customLocationCard: MaterialCardView
    private lateinit var citySearchEditText: TextInputEditText
    private lateinit var searchCityButton: MaterialButton
    private lateinit var searchResultsLabel: TextView
    private lateinit var searchResultsChipGroup: ChipGroup
    private lateinit var selectedLocationText: TextView
    private lateinit var refreshLocationButton: MaterialButton
    private lateinit var saveSettingsButton: MaterialButton
    private lateinit var updateWidgetsButton: MaterialButton
    private lateinit var statusText: TextView

    private var currentLocationData: LocationData? = null
    private var selectedCustomLocation: LocationData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            initViews()
            setupEventListeners()
            loadCurrentSettings()
            
            // Check permissions safely without forcing location request on startup
            checkPermissionsQuietly()
            
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
        refreshLocationButton = findViewById(R.id.refreshLocationButton)
        saveSettingsButton = findViewById(R.id.saveSettingsButton)
        updateWidgetsButton = findViewById(R.id.updateWidgetsButton)
        statusText = findViewById(R.id.statusText)
        
        currentLocationText.text = "Ready to detect location"
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
        showStatus("Selected: ${location.city}")
    }

    private fun setupEventListeners() {
        locationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAutoLocation -> {
                    customLocationCard.visibility = View.GONE
                    clearSearchResults()
                    showStatus("GPS location selected")
                }
                R.id.radioCustomLocation -> {
                    customLocationCard.visibility = View.VISIBLE
                    showStatus("Custom location selected")
                }
            }
        }

        refreshLocationButton.setOnClickListener {
            if (radioAutoLocation.isChecked) {
                requestLocationSafely()
            } else {
                showStatus("Please select GPS location mode to refresh")
            }
        }

        saveSettingsButton.setOnClickListener {
            saveCurrentSettings()
        }

        updateWidgetsButton.setOnClickListener {
            updateAllWidgets()
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

    private fun loadCurrentSettings() {
        try {
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
                }
            } else {
                radioAutoLocation.isChecked = true
                customLocationCard.visibility = View.GONE
            }
        } catch (e: Exception) {
            showStatus("Error loading settings: ${e.message}")
            // Set safe defaults
            radioAutoLocation.isChecked = true
            customLocationCard.visibility = View.GONE
        }
    }

    private fun checkPermissionsQuietly() {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasLocationPermission) {
            showStatus("Location permission available", 2000)
        } else {
            showStatus("Location permission not granted. Use 'Refresh Location' to request.", 3000)
        }
    }

    private fun requestLocationSafely() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        }
    }

    private fun loadCurrentLocationSafely() {
        showStatus("Getting your location...")
        lifecycleScope.launch {
            try {
                val location = WeatherLocationManager.getCurrentLocation(this@MainActivity)
                currentLocationData = location
                updateLocationDisplay(location)
                showStatus("Location updated successfully!", 2000)
            } catch (e: Exception) {
                showStatus("Error getting location: ${e.message}", 3000)
                currentLocationText.text = "Error loading location - using fallback"
                // Try to get a fallback location
                try {
                    val fallback = LocationData(51.5074, -0.1278, "London (Fallback)")
                    currentLocationData = fallback
                    updateLocationDisplay(fallback)
                } catch (fallbackError: Exception) {
                    currentLocationText.text = "Unable to determine location"
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

    private fun saveCurrentSettings() {
        try {
            when (locationRadioGroup.checkedRadioButtonId) {
                R.id.radioAutoLocation -> {
                    WeatherLocationManager.saveLocationPreference(this, false)
                    showStatus("Settings saved! Using GPS location.", 2000)
                    updateAllWidgets()
                }
                R.id.radioCustomLocation -> {
                    if (selectedCustomLocation != null) {
                        WeatherLocationManager.saveLocationPreference(this, true, selectedCustomLocation)
                        currentLocationData = selectedCustomLocation
                        updateLocationDisplay(selectedCustomLocation!!)
                        showStatus("Settings saved! Using ${selectedCustomLocation!!.city}.", 2000)
                        updateAllWidgets()
                    } else {
                        showStatus("Please select a location first")
                    }
                }
            }
        } catch (e: Exception) {
            showStatus("Error saving settings: ${e.message}")
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
                    loadCurrentLocationSafely()
                } else {
                    showStatus("Location permission denied. Using fallback location.", 3000)
                    // Load fallback location
                    try {
                        val fallback = LocationData(51.5074, -0.1278, "London (Fallback)")
                        currentLocationData = fallback
                        updateLocationDisplay(fallback)
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