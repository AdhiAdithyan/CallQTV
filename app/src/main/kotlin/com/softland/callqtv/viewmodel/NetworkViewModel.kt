package com.softland.callqtv.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel

class NetworkViewModel : ViewModel() {
    private var cachedNetworkLiveData: NetworkLiveData? = null

    /** Returns a cached connectivity LiveData instance scoped to application context. */
    fun getNetworkLiveData(context: Context): LiveData<Boolean> {
        return cachedNetworkLiveData ?: NetworkLiveData(context.applicationContext).also {
            cachedNetworkLiveData = it
        }
    }
}
