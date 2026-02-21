package com.softland.callqtv.data.repository

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClientManager(
    private val context: Context,
    val serverUri: String,
    val clientId: String
) {

    interface MqttListener {
        fun onMessageReceived(topic: String, message: String)
        fun onConnectionStatus(isConnected: Boolean)
        fun onError(error: String)
        fun onAutoRetryExhausted()
    }

    private var mqttClient: MqttAsyncClient? = null
    private var mqttListener: MqttListener? = null
    private var isConnecting = false
    private var topicsToSubscribe: String? = null
    private var qosToSubscribe: Int = 1
    private var isInitialized = false

    init {
        Log.d(TAG, "Initializing MQTT Client: serverUri=$serverUri, clientId=$clientId")
        initializeMqttClient()
    }

    fun setMqttListener(listener: MqttListener) {
        this.mqttListener = listener
    }

    private fun initializeMqttClient() {
        try {
            mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence()).apply {
                setCallback(object : MqttCallbackExtended {
                    override fun connectionLost(cause: Throwable?) {
                        val errorMsg = cause?.message ?: "Unknown reason"
                        Log.e(TAG, "!!! MQTT CONNECTION LOST !!! Reason: $errorMsg", cause)
                        mqttListener?.let {
                            it.onConnectionStatus(false)
                            it.onError("Connection lost: $errorMsg")
                            it.onAutoRetryExhausted()
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val payload = message?.payload?.let { String(it) } ?: ""
                        Log.i(TAG, "!!! INCOMING MQTT MESSAGE !!! Topic: [$topic], Payload: $payload")
                        mqttListener?.onMessageReceived(topic.orEmpty(), payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "Delivery complete")
                    }

                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.i(TAG, "==> MQTT CONNECT COMPLETE (Extended) <== Reconnect: $reconnect, URI: $serverURI")
                        isConnecting = false
                        mqttListener?.onConnectionStatus(true)
                        
                        val topics = topicsToSubscribe
                        if (topics != null) {
                            Log.d(TAG, "Auto-subscribing to: $topics")
                            subscribe(topics, qosToSubscribe)
                        } else {
                            Log.w(TAG, "No topics found to subscribe to in connectComplete!")
                        }
                    }
                })
            }
            isInitialized = true
        } catch (e: MqttException) {
            Log.e(TAG, "Error creating MQTT client", e)
            isInitialized = false
        }
    }

    fun connect(username: String?, password: String?) {
        if (!isInitialized) {
            val errorText = "MQTT client not initialized, cannot connect."
            Log.e(TAG, errorText)
            mqttListener?.onError(errorText)
            mqttListener?.onAutoRetryExhausted()
            return
        }

        if (isConnected()) {
            Log.i(TAG, "Already connected to MQTT broker")
            mqttListener?.onConnectionStatus(true)
            topicsToSubscribe?.let { subscribe(it, qosToSubscribe) }
            return
        }
        
        if (isConnecting) {
            Log.d(TAG, "Connection attempt already in progress, ignoring this request")
            return
        }

        Log.i(TAG, "Connecting to MQTT broker... serverUri=$serverUri, username=${username ?: "null"}")
        isConnecting = true
        
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 30
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                userName = username
                this.password = password.toCharArray()
            }
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnecting = false
                    Log.i(TAG, "==> INITIAL MQTT CONNECTION SUCCESS <== Server: $serverUri")
                    
                    // Signal success to listener immediately
                    mqttListener?.onConnectionStatus(true)
                    
                    topicsToSubscribe?.let {
                        Log.d(TAG, "Subscription backup trigger for: $it")
                        subscribe(it, qosToSubscribe)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    val errorText = "Initial connection failed: ${exception?.message ?: "Unknown error"}"
                    Log.e(TAG, errorText, exception)
                    mqttListener?.let {
                        it.onConnectionStatus(false)
                        it.onError(errorText)
                        it.onAutoRetryExhausted()
                    }
                }
            })
        } catch (e: Exception) {
            isConnecting = false
            val errorText = "Connection exception: ${e.message}"
            Log.e(TAG, errorText, e)
            mqttListener?.onConnectionStatus(false)
            mqttListener?.onError(errorText)
            mqttListener?.onAutoRetryExhausted()
        }
    }

    fun subscribe(topic: String, qos: Int) {
        val cleanTopic = topic.trim()
        Log.i(TAG, "REQUESTING SUBSCRIPTION to: '$cleanTopic' (QoS: $qos)")
        this.topicsToSubscribe = cleanTopic
        this.qosToSubscribe = qos
        
        if (!isConnected()) {
            Log.d(TAG, "Subscription queued (Client not yet connected to $serverUri)")
            return
        }

        val topics = cleanTopic.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
        if (topics.isEmpty()) {
            Log.e(TAG, "!!! SUBSCRIPTION ABORTED: Topic string is empty !!!")
            return
        }

        Log.i(TAG, "EXECUTING Paho Subscribe for topics: ${topics.joinToString()}")
        try {
            val qosArray = IntArray(topics.size) { qos }
            mqttClient?.subscribe(topics, qosArray, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    val granted = asyncActionToken?.grantedQos
                    val grantedStr = granted?.contentToString() ?: "unknown"
                    
                    if (granted?.contains(128) == true) {
                        Log.e(TAG, "--- SUBSCRIPTION REJECTED BY BROKER (QoS 128) --- Topic: ${topics.joinToString()}, Granted: $grantedStr")
                        mqttListener?.onError("Subscription rejected by broker for topics: ${topics.joinToString()}")
                    } else {
                        Log.i(TAG, "+++ SUCCESS: Subscribed to ${topics.joinToString()} (Granted QoS: $grantedStr) +++")
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val error = "--- FAILURE: Subscription failed for ${topics.joinToString()}. Error: ${exception?.message} ---"
                    Log.e(TAG, error, exception)
                    mqttListener?.onError(error)
                }
            })
        } catch (e: Exception) {
            val error = "!!! EXCEPTION during subscribe call: ${e.message} !!!"
            Log.e(TAG, error, e)
            mqttListener?.onError(error)
        }
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting from MQTT broker...")
        isConnecting = false
        try {
            mqttClient?.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Disconnected successfully")
                    mqttListener?.onConnectionStatus(false)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val error = "Disconnect failed: ${exception?.message}"
                    Log.e(TAG, error, exception)
                    mqttListener?.onError(error)
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "Disconnect exception: ${e.message}", e)
        }
    }

    fun close() {
        Log.d(TAG, "Closing MQTT client")
        isConnecting = false
        val client = mqttClient
        mqttClient = null
        mqttListener = null
        try {
            if (client?.isConnected == true) {
                client.disconnect(500).waitForCompletion(500)
            }
            client?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error while closing MQTT client: ${e.message}")
            try { client?.close() } catch (_: Exception) {}
        }
    }

    fun isConnected(): Boolean = mqttClient?.isConnected == true

    companion object {
        private const val TAG = "MqttClientManager"
    }
}
