package com.softland.callqtv.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.data.model.ServiceUrlResponse
import com.softland.callqtv.data.repository.ServiceUrlRepository
import kotlinx.coroutines.launch

class ServiceUrlViewModel : ViewModel() {
    private val repository = ServiceUrlRepository()
    
    private val _serviceUrlResponse = MutableLiveData<ServiceUrlResponse?>()
    val serviceUrlResponse: LiveData<ServiceUrlResponse?> = _serviceUrlResponse

    fun fetchServiceUrl(loginUrl: String, loginId: String) {
        viewModelScope.launch {
            val response = repository.getServiceUrl(loginUrl, loginId)
            _serviceUrlResponse.value = response
        }
    }
}
