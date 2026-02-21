package com.softland.callqtv.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

class NetworkViewModel : ViewModel() {
    private var cachedNetworkLiveData: NetworkLiveData? = null

    fun getNetworkLiveData(context: Context): LiveData<Boolean> {
        return cachedNetworkLiveData ?: NetworkLiveData(context.applicationContext).also {
            cachedNetworkLiveData = it
        }
    }
}
