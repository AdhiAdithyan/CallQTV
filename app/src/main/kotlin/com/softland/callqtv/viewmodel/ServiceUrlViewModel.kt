package com.softland.callqtv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.data.model.ServiceUrlResponse
import com.softland.callqtv.data.repository.ServiceUrlRepository
import kotlinx.coroutines.launch

class ServiceUrlViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServiceUrlRepository(application)
    
    private val _serviceUrlResponse = MutableLiveData<ServiceUrlResponse?>()
    val serviceUrlResponse: LiveData<ServiceUrlResponse?> = _serviceUrlResponse

    fun fetchServiceUrl(loginUrl: String, loginId: String) {
        viewModelScope.launch {
            val response = repository.getServiceUrl(loginUrl, loginId)
            _serviceUrlResponse.value = response
        }
    }
}
