package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class LicenseCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val _daysRemaining = MutableLiveData<Int?>()
    val daysRemaining: LiveData<Int?> = _daysRemaining

    private val _isExpired = MutableLiveData<Boolean>(false)
    val isExpired: LiveData<Boolean> = _isExpired
    
    // For backward compatibility with SplashScreenActivity
    private val _isLicenseValid = MutableLiveData<Boolean>(false)
    val isLicenseValid: LiveData<Boolean> = _isLicenseValid

    private val authPrefs = application.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)

    init {
        startCheck()
    }

    private fun startCheck() {
        viewModelScope.launch {
            while (true) {
                checkLicense()
                delay(60 * 60 * 1000L) // Check every hour
            }
        }
    }

    /**
     * Checks license validity and updates LiveData.
     */
    fun checkLicense() {
        val rawEnd = authPrefs.getString(PreferenceHelper.product_license_end, null).orEmpty()
        checkLicenseValidity(rawEnd)
    }

    /**
     * Public method matching SplashScreenActivity usage.
     */
    fun checkLicenseValidity(rawEnd: String) {
        if (rawEnd.isBlank()) {
            _daysRemaining.value = null
            _isExpired.value = true
            _isLicenseValid.value = false
            return
        }

        try {
            val datePart = rawEnd.trim().split(" ").getOrElse(0) { "" }
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            val endDate = LocalDate.parse(datePart, formatter)
            val today = LocalDate.now()
            val days = ChronoUnit.DAYS.between(today, endDate).toInt()
            
            _daysRemaining.value = days
            _isExpired.value = days < 0
            _isLicenseValid.value = days >= 0
        } catch (e: Exception) {
            _daysRemaining.value = null
            _isExpired.value = true
            _isLicenseValid.value = false
        }
    }
}
