package com.softland.callqtv.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.LiveData
import com.softland.callqtv.utils.NetworkCompat

class NetworkLiveData(context: Context) : LiveData<Boolean>() {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Evaluates whether a given network currently has usable validated internet. */
    private fun checkNetwork(network: Network) {
        val capabilities = NetworkCompat.networkCapabilities(connectivityManager, network)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isValidated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } else {
            hasInternet
        }
        postValue(hasInternet && isValidated)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkNetwork(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            checkNetwork(network)
        }

        override fun onLost(network: Network) {
            postValue(false)
        }
    }

    /** Registers callbacks and publishes initial connectivity state when observed. */
    override fun onActive() {
        super.onActive()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                checkNetwork(activeNetwork)
            } else {
                postValue(false)
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } else {
            postValue(NetworkCompat.isNetworkAvailable(appContext))
        }
    }

    /** Unregisters network callbacks when there are no active observers. */
    override fun onInactive() {
        super.onInactive()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // ignore
        }
    }
}
