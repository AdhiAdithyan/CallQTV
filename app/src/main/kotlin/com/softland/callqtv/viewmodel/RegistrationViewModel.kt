package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.model.CheckDeviceStatusRequest
import com.softland.callqtv.data.model.DeviceRegistrationRequest
import com.softland.callqtv.data.model.ProductAuthenticationReq
import com.softland.callqtv.data.repository.ProjectRepository
import com.softland.callqtv.data.repository.ServiceUrlRepository
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale

sealed class RegistrationState {
    object Idle : RegistrationState()
    data class Loading(val message: String) : RegistrationState()
    data class Error(val message: String, val canRetry: Boolean = true) : RegistrationState()
    data class ProductAuthRequired(val status: String) : RegistrationState()
    data class DeviceApprovalRequired(val status: String) : RegistrationState()
    data class LicenseExpired(val status: String) : RegistrationState()
    data class UpdateAvailable(
        val apkVersion: String,
        val downloadURL: String,
        val isMandatoryUpdate: Int,
        val projectCode: String
    ) : RegistrationState()
    object NavigateToMain : RegistrationState()
}

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val projectRepository = ProjectRepository()
    private val serviceRepository = ServiceUrlRepository()
    private val authPrefs = application.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
    private val gson = Gson()

    private var lastAuthRes: com.softland.callqtv.data.model.ProductAuthenticationRes? = null
    private var lastRegRes: com.softland.callqtv.data.model.DeviceRegistrationResponse? = null

    private val _customerId = MutableLiveData("")
    val customerId: LiveData<String> = _customerId

    private val _customerIdError = MutableLiveData<String?>(null)
    val customerIdError: LiveData<String?> = _customerIdError

    private val _state = MutableLiveData<RegistrationState>(RegistrationState.Idle)
    val state: LiveData<RegistrationState> = _state

    fun setCustomerId(id: String) {
        _customerId.value = id
        if (id.length == 4) {
            _customerIdError.value = null
        }
    }

    fun resetState() {
        _state.value = RegistrationState.Idle
    }

    fun startRegistrationFlow() {
        val id = _customerId.value ?: ""
        if (id.length != 4) {
            _customerIdError.value = "Customer ID must be 4 digits"
            return
        }

        val customerIdInt = id.toIntOrNull() ?: 0

        // Persist customer ID immediately so it's remembered for the next session
        authPrefs.edit().putInt(PreferenceHelper.customer_id, customerIdInt).apply()

        viewModelScope.launch {
            val macAddress = withContext(Dispatchers.IO) { Variables.getMacId(getApplication()) }
            _state.value = RegistrationState.Loading("Authenticating product...")
            
            try {
                // 1. Product Authentication
                val authReq = ProductAuthenticationReq().apply {
                    this.uniqueIDentifier = macAddress
                    this.customerId = customerIdInt
                }
                val authRes = projectRepository.authenticateProduct(Variables.getLicenseBaseUrl() + "ProductAuthentication", authReq)
                lastAuthRes = authRes

                if (authRes.authenticationstatus == "Approve" ) {
                    // Success, move to registration
                    performDeviceRegistration(macAddress, customerIdInt, authRes)
                } else {
                    val msg = authRes.qms ?: "Authentication failed"
                    com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Product Auth Denied: $msg")
                    _state.value = RegistrationState.ProductAuthRequired(msg)
                }
            } catch (e: HttpException) {
                val errBody = e.response()?.errorBody()?.string() ?: ""
                com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "HTTP Auth Error: $errBody", e)
                
                var displayMsg = "Auth HTTP ${e.code()}"
                try {
                    val map = gson.fromJson(errBody, Map::class.java)
                    val msg = map["message"] as? String ?: map["error"] as? String
                    if (!msg.isNullOrBlank()) displayMsg = msg
                } catch (_: Exception) {}
                
                _state.value = RegistrationState.Error(displayMsg)
            } catch (e: Exception) {
                com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Auth Exception", e)
                _state.value = RegistrationState.Error("Auth failed: ${e.message}")
            }
        }
    }

    private suspend fun performDeviceRegistration(macAddress: String, customerId: Int, authRes: com.softland.callqtv.data.model.ProductAuthenticationRes) {
        _state.value = RegistrationState.Loading("Registering device...")
        try {
            val regReq = DeviceRegistrationRequest().apply {
                this.uniqueIDentifier = authRes.uniqueIDentifier ?: macAddress
                this.deviceIdentifier1 = macAddress
                this.productRegistrationId = authRes.productRegistrationId ?: 0
                this.deviceType = 3 // TV
            }
            
            val regRes = projectRepository.getDeviceRegistration(Variables.getLicenseBaseUrl() + "DeviceRegistration", regReq)
            lastRegRes = regRes

            when (regRes.status) {
                "Success" -> {
                    checkDeviceStatus(macAddress, customerId, authRes, regRes)
                }
                "Block" -> {
                    val msg = regRes.message ?: "Awaiting approval"
                    com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Device Blocked: $msg")
                    _state.value = RegistrationState.DeviceApprovalRequired(msg)
                }
                else -> {
                    val msg = regRes.message ?: "Registration error"
                    com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Registration Fail: $msg")
                    _state.value = RegistrationState.Error(msg)
                }
            }
        } catch (e: HttpException) {
            val errBody = e.response()?.errorBody()?.string() ?: ""
            com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "HTTP Reg Error: $errBody", e)
            
            var displayMsg = "Reg HTTP ${e.code()}"
            try {
                val map = gson.fromJson(errBody, Map::class.java)
                val msg = map["message"] as? String ?: map["error"] as? String
                if (!msg.isNullOrBlank()) displayMsg = msg
            } catch (_: Exception) {}
            
            _state.value = RegistrationState.Error(displayMsg)
        } catch (e: Exception) {
            com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Reg Exception", e)
            _state.value = RegistrationState.Error("Registration failed: ${e.message}")
        }
    }

    private suspend fun checkDeviceStatus(
        macAddress: String,
        customerId: Int,
        authRes: com.softland.callqtv.data.model.ProductAuthenticationRes,
        regRes: com.softland.callqtv.data.model.DeviceRegistrationResponse
    ) {
        _state.value = RegistrationState.Loading("Checking device status...")
        try {
            val statusReq = CheckDeviceStatusRequest().apply {
                this.uniqueIDentifier = regRes.uniqueIDentifier?.toString()
                this.customerId = customerId.toString()
                this.productRegistrationId = authRes.productRegistrationId?.toString()
                this.deviceRegistrationId = regRes.deviceRegistrationId ?: 0
                this.projectNAme = regRes.projectName
            }
            val statusRes = projectRepository.getCheckDeviceStatus(Variables.getLicenseBaseUrl() + "CheckDeviceStatus", statusReq)

            if (statusRes.status == 1 || statusRes.status == 3) {
                saveAuthDetails(customerId, statusRes)
                
                val currentVersion = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName
                val serverVersion = statusRes.apkVersion
                if (serverVersion != null && serverVersion != currentVersion) {
                    _state.value = RegistrationState.UpdateAvailable(
                        apkVersion = serverVersion,
                        downloadURL = statusRes.downloadUrl ?: "",
                        isMandatoryUpdate = statusRes.isMandatoryUpdate?.toIntOrNull() ?: 0,
                        projectCode = statusRes.projectCode ?: ""
                    )
                } else {
                    fetchServiceUrl(statusRes.projectCode ?: "")
                }
            } else if (statusRes.status == 7) {
                _state.value = RegistrationState.LicenseExpired(statusRes.message ?: "License expired")
            } else {
                val msg = statusRes.message ?: "Status check failed"
                com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Status Check Fail: $msg")
                _state.value = RegistrationState.Error(msg)
            }
        } catch (e: HttpException) {
            val errBody = e.response()?.errorBody()?.string() ?: ""
            com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "HTTP Status Error: $errBody", e)
            
            var displayMsg = "Status HTTP ${e.code()}"
            try {
                val map = gson.fromJson(errBody, Map::class.java)
                val msg = map["message"] as? String ?: map["error"] as? String
                if (!msg.isNullOrBlank()) displayMsg = msg
            } catch (_: Exception) {}
            
            _state.value = RegistrationState.Error(displayMsg)
        } catch (e: Exception) {
            com.softland.callqtv.utils.FileLogger.logError(getApplication(), "RegViewModel", "Status Exception", e)
            _state.value = RegistrationState.Error("Status check failed: ${e.message}")
        }
    }

    fun retryRegistrationFlow() {
        val macAddress = Variables.getMacId(getApplication())
        val id = _customerId.value ?: ""
        val customerIdInt = id.toIntOrNull() ?: 0
        
        val currentState = _state.value
        if (currentState is RegistrationState.Error) {
            viewModelScope.launch {
                if (lastAuthRes == null) {
                    startRegistrationFlow()
                } else if (lastRegRes == null) {
                    performDeviceRegistration(macAddress, customerIdInt, lastAuthRes!!)
                } else {
                    checkDeviceStatus(macAddress, customerIdInt, lastAuthRes!!, lastRegRes!!)
                }
            }
        }
    }

    fun fetchServiceUrl(projectCode: String) {
        if (projectCode.isEmpty()) {
            _state.value = RegistrationState.NavigateToMain
            return
        }

        viewModelScope.launch {
            var isFetched = false
            while (!isFetched) {
                _state.value = RegistrationState.Loading("Fetching service URL...")
                val res = serviceRepository.getServiceUrl(projectCode, Variables.LoadURL() + "GetServiceUrl/")
                val newUrl = res?.projectDetails?.serviceUrl
                
                if (!newUrl.isNullOrEmpty()) {
                    authPrefs.edit().putString(PreferenceHelper.base_url, newUrl).apply()
                    isFetched = true
                    _state.value = RegistrationState.NavigateToMain
                } else {
                    // Connection failed or response doesn't have base URL
                    if (Variables.ServiceCounter < 4) {
                        Variables.ServiceCounter++
                        delay(500L)
                    } else {
                        Variables.ServiceCounter = 0
                        isFetched = true // Stop retrying after reaching limit
                        _state.value = RegistrationState.NavigateToMain
                    }
                }
            }
        }
    }

    private fun saveAuthDetails(customerId: Int, res: com.softland.callqtv.data.model.CheckDeviceStatusResponse) {
        authPrefs.edit().apply {
            putInt(PreferenceHelper.customer_id, customerId)
            putString(PreferenceHelper.product_license_end, res.licenceActiveTo)
            putString(PreferenceHelper.project_code, res.projectCode)
            apply()
        }
    }
}
