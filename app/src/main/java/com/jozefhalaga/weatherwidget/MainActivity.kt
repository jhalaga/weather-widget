package com.jozefhalaga.weatherwidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val LOCATION_REQUEST = 42

    private lateinit var currentLocationText: TextView
    private lateinit var locationRadioGroup: RadioGroup
    private lateinit var radioAutoLocation: RadioButton
    private lateinit var radioCustomLocation: RadioButton
    private lateinit var customLocationCard: MaterialCardView
    private lateinit var citySpinner: Spinner
    private lateinit var selectedLocationText: TextView
    private lateinit var refreshLocationButton: MaterialButton
    private lateinit var saveSettingsButton: MaterialButton
    private lateinit var statusText: TextView

    private var currentLocationData: LocationData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_main)
            
            initViews()
            setupSpinner()
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
        citySpinner = findViewById(R.id.citySpinner)
        selectedLocationText = findViewById(R.id.selectedLocationText)
        refreshLocationButton = findViewById(R.id.refreshLocationButton)
        saveSettingsButton = findViewById(R.id.saveSettingsButton)
        statusText = findViewById(R.id.statusText)
        
        currentLocationText.text = "Ready to detect location"
    }

    private fun setupSpinner() {
        try {
            val cities = WeatherLocationManager.popularCities
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                cities.map { it.city }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            citySpinner.adapter = adapter

            citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        val selectedCity = cities[position]
                        selectedLocationText.text = "Lat: ${selectedCity.latitude}, Lon: ${selectedCity.longitude}"
                        selectedLocationText.visibility = View.VISIBLE
                    } catch (e: Exception) {
                        showStatus("Error selecting city: ${e.message}")
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            showStatus("Error setting up city selector: ${e.message}")
        }
    }

    private fun setupEventListeners() {
        locationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAutoLocation -> {
                    customLocationCard.visibility = View.GONE
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
    }

    private fun loadCurrentSettings() {
        try {
            val isUsingCustom = WeatherLocationManager.isUsingCustomLocation(this)
            
            if (isUsingCustom) {
                radioCustomLocation.isChecked = true
                customLocationCard.visibility = View.VISIBLE
                
                val customLocation = WeatherLocationManager.getCustomLocation(this)
                if (customLocation != null) {
                    // Find the matching city in spinner
                    val cities = WeatherLocationManager.popularCities
                    val index = cities.indexOfFirst { 
                        Math.abs(it.latitude - customLocation.latitude) < 0.1 && 
                        Math.abs(it.longitude - customLocation.longitude) < 0.1 
                    }
                    if (index >= 0) {
                        citySpinner.setSelection(index)
                    }
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
                }
                R.id.radioCustomLocation -> {
                    val selectedPosition = citySpinner.selectedItemPosition
                    val selectedCity = WeatherLocationManager.popularCities[selectedPosition]
                    WeatherLocationManager.saveLocationPreference(this, true, selectedCity)
                    currentLocationData = selectedCity
                    updateLocationDisplay(selectedCity)
                    showStatus("Settings saved! Using ${selectedCity.city}.", 2000)
                }
            }
        } catch (e: Exception) {
            showStatus("Error saving settings: ${e.message}")
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