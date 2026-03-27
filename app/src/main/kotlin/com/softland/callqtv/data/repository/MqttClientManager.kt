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
        /** Fires for every packet on a subscribed topic before retained filtering (use for liveness). */
        fun onAnyIncomingMqttTraffic() {}
        fun onMessageReceived(topic: String, message: String)
        fun onConnectionStatus(isConnected: Boolean)
        fun onError(error: String, code: Int? = null)
        fun onAutoRetryExhausted()
    }

    private var mqttClient: MqttAsyncClient? = null
    private var mqttListener: MqttListener? = null
    private var isConnecting = false
    private var topicsToSubscribe: String? = null
    private var qosToSubscribe: Int = 1
    private var isInitialized = false
    private var hasConnectedOnce = false

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
                        Log.w(TAG, "MQTT connection lost (will rely on auto-reconnect). Reason: $errorMsg", cause)
                        mqttListener?.let {
                            it.onConnectionStatus(false)
                            val code = (cause as? MqttException)?.reasonCode
                            // Surface as a soft warning; do NOT mark auto-retry as exhausted here
                            it.onError("Connection lost: $errorMsg", code)
                        }
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        mqttListener?.onAnyIncomingMqttTraffic()

                        if (message?.isRetained == true) {
                            Log.d(TAG, "Skipping retained message (stale payload on reconnect)")
                            return
                        }

                        val payload = message?.payload?.let { String(it) } ?: ""

                        // Do NOT filter here. Forward ALL non-retained payloads to the ViewModel,
                        // which maintains its own queue and applies business filters.
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
            mqttListener?.onError(errorText, -1)
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
            isCleanSession = false // Enable persistent session to receive missed messages on reconnect
            isAutomaticReconnect = true
            // Use a faster initial timeout so unreachable brokers fail quickly and retry sooner.
            // This improves first-boot connect latency on unstable LAN/Wi-Fi.
            connectionTimeout = 10
            maxReconnectDelay = 8
            // Very short keep-alive so PINGREQ is sent about every 120 seconds.
            // Note: this increases network chatter and sensitivity to brief hiccups.
            keepAliveInterval = 120
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
                    hasConnectedOnce = true
                    Log.i(TAG, "==> INITIAL MQTT CONNECTION SUCCESS <== Server: $serverUri")
                    
                    mqttListener?.onConnectionStatus(true)
                    
                    topicsToSubscribe?.let {
                        Log.d(TAG, "Subscription backup trigger for: $it")
                        subscribe(it, qosToSubscribe)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnecting = false
                    val rawMsg = exception?.message ?: "Unknown error"
                    val causeMsg = exception?.cause?.message.orEmpty()
                    val code = (exception as? MqttException)?.reasonCode

                    // Retryable connect failures should be treated as soft errors so the retry loop
                    // can continue (instead of marking auto-retry exhausted).
                    val isRetryableConnectFailure = isRetryableConnectFailure(
                        exception = exception,
                        rawMsg = rawMsg,
                        causeMsg = causeMsg,
                        reasonCode = code
                    )

                    val errorText = if (hasConnectedOnce) {
                        "Reconnect attempt failed: $rawMsg"
                    } else {
                        "Initial connection failed: $rawMsg"
                    }
                    Log.w(TAG, "[$serverUri] onFailure: $errorText (Code: $code, Cause: $causeMsg)")

                    mqttListener?.let {
                        it.onConnectionStatus(false)
                        it.onError(errorText, code)

                        if (isRetryableConnectFailure) {
                            // Network / broker unreachable: rely on automatic reconnect + our own retry logic.
                            Log.w(TAG, "MQTT broker unreachable (soft failure, will retry). Details: $rawMsg", exception)
                        } else {
                            Log.e(TAG, errorText, exception)
                            it.onAutoRetryExhausted()
                        }
                    }
                }
            })
        } catch (e: MqttException) {
            val isAlreadyConnecting = e.reasonCode == 32110 ||
                    e.message?.contains("Connect already in progress") == true ||
                    e.cause?.message?.contains("Connect already in progress") == true

            if (isAlreadyConnecting) {
                Log.d(TAG, "MQTT connection is already in progress (Paho auto-reconnect or duplicate call). Ignoring.")
                // Do not reset isConnecting here, since a connection is genuinely in progress.
                return
            }

            isConnecting = false
            val errorText = "Connection exception: ${e.message}"
            Log.e(TAG, errorText, e)
            mqttListener?.onConnectionStatus(false)
            mqttListener?.onError(errorText, e.reasonCode)
            if (!isRetryableConnectFailure(
                    exception = e,
                    rawMsg = e.message.orEmpty(),
                    causeMsg = e.cause?.message.orEmpty(),
                    reasonCode = e.reasonCode
                )
            ) {
                mqttListener?.onAutoRetryExhausted()
            }
        } catch (e: Exception) {
            isConnecting = false
            val errorText = "Connection exception: ${e.message}"
            Log.e(TAG, errorText, e)
            mqttListener?.onConnectionStatus(false)
            mqttListener?.onError(errorText)
            if (!isRetryableConnectFailure(
                    exception = e,
                    rawMsg = e.message.orEmpty(),
                    causeMsg = e.cause?.message.orEmpty(),
                    reasonCode = null
                )
            ) {
                mqttListener?.onAutoRetryExhausted()
            }
        }
    }

    private fun isRetryableConnectFailure(
        exception: Throwable?,
        rawMsg: String,
        causeMsg: String,
        reasonCode: Int?
    ): Boolean {
        val msg = "$rawMsg $causeMsg".lowercase()
        return reasonCode == 32103 || // Paho: unable to connect
                exception is java.net.SocketTimeoutException ||
                exception?.cause is java.net.SocketTimeoutException ||
                msg.contains("sockettimeoutexception") ||
                msg.contains("timed out") ||
                msg.contains("ehostunreach") ||
                msg.contains("no route to host") ||
                msg.contains("connection refused") ||
                msg.contains("connectexception") ||
                msg.contains("network is unreachable")
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

    fun publish(topic: String, payload: String, qos: Int = 0) {
        if (!isConnected()) {
            Log.w(TAG, "Cannot publish to $topic: Client not connected")
            return
        }

        try {
            val message = MqttMessage(payload.toByteArray())
            message.qos = qos
            message.isRetained = false
            
            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Successfully published message to $topic: $payload")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to publish message to $topic", exception)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception during publish", e)
        }
    }

    fun disconnect() {
        // Do not actively disconnect from the broker on user/UI requests anymore.
        // We rely on Paho's automatic reconnect and higher-level reachability pings instead
        // of forcing a disconnect, which was causing unnecessary flapping.
        Log.i(TAG, "disconnect() called – ignoring (ping/reconnect strategy in use).")
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
