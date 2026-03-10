package com.softland.callqtv.fcm

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.softland.callqtv.data.repository.TvConfigRepository
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables
import com.softland.callqtv.data.local.AppSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TvConfigRepository

    override fun onCreate() {
        super.onCreate()
        repository = TvConfigRepository(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: " + remoteMessage.data)
            triggerConfigUpdate()
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            triggerConfigUpdate()
        }
    }

    private fun triggerConfigUpdate() {
        val authPrefs = getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val customerId = authPrefs.getString(PreferenceHelper.customer_id, "") ?: ""
        val macAddress = Variables.getMacId(applicationContext)

        if (customerId.isNotEmpty()) {
            serviceScope.launch {
                try {
                    // This call now includes the fcm_token in the request body
                    repository.fetchAndCacheTvConfig(macAddress, customerId)
                    Log.d("FCM", "TV Config (and token) updated successfully")
                } catch (e: Exception) {
                    Log.e("FCM", "Failed to update TV Config via FCM trigger", e)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        
        // 1. Save the new token locally
        PreferenceHelper.saveFcmToken(applicationContext, token)
        
        // 2. Proactively push the new token to the server
        triggerConfigUpdate()
    }
}
