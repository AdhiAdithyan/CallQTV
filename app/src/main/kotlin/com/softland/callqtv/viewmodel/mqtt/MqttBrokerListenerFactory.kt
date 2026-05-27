package com.softland.callqtv.viewmodel.mqtt

import com.softland.callqtv.data.repository.MqttClientManager
import com.softland.callqtv.viewmodel.KeypadPayloadParser
/**
 * Host for one broker URI: connection events + inbound payload handling.
 * Implemented by [com.softland.callqtv.viewmodel.MqttViewModel] (per-broker delegate).
 */
internal interface MqttBrokerCallbacks {
    val serverUri: String

    fun isLicenseExpired(): Boolean
    fun onAnyIncomingTraffic()
    fun logPayloadIn(trimmed: String)
    fun enqueueRawPayload(trimmed: String)
    fun launchBackground(block: suspend () -> Unit)

    /** CLR / refresh / verified token path (runs on Default dispatcher). */
    suspend fun processInboundPayload(topic: String, trimmed: String)

    fun onConnectionStatus(isConnected: Boolean)
    fun onBrokerError(error: String, code: Int?)
    fun onAutoRetryExhausted()
}

internal object MqttBrokerListenerFactory {

    /** Builds a broker listener that forwards events to [MqttBrokerCallbacks]. */
    fun create(callbacks: MqttBrokerCallbacks): MqttClientManager.MqttListener =
        object : MqttClientManager.MqttListener {
            override fun onAnyIncomingMqttTraffic() {
                callbacks.onAnyIncomingTraffic()
            }

            override fun onMessageReceived(topic: String, message: String) {
                callbacks.onAnyIncomingTraffic()
                val trimmed = message.trim()
                android.util.Log.i("MQTT_PAYLOAD_IN", trimmed)
                callbacks.logPayloadIn(trimmed)
                if (callbacks.isLicenseExpired()) return
                callbacks.enqueueRawPayload(trimmed)
                callbacks.launchBackground {
                    callbacks.processInboundPayload(topic, trimmed)
                }
            }

            override fun onConnectionStatus(isConnected: Boolean) {
                callbacks.onConnectionStatus(isConnected)
            }

            override fun onError(error: String, code: Int?) {
                callbacks.onBrokerError(error, code)
            }

            override fun onAutoRetryExhausted() {
                callbacks.onAutoRetryExhausted()
            }
        }
}

internal object MqttBrokerConnector {

    /** Indicates whether an existing manager can be reused or must be replaced. */
    sealed class ExistingManagerResult {
        data object Reattached : ExistingManagerResult()
        data object MustReplace : ExistingManagerResult()
    }

    /**
     * If a manager already exists for this URI, re-subscribe or reconnect; otherwise replace.
     */
    fun handleExistingManager(
        existing: MqttClientManager?,
        clientId: String,
        previousDetails: com.softland.callqtv.viewmodel.mqtt.MqttConnectionDetails?,
        newDetails: com.softland.callqtv.viewmodel.mqtt.MqttConnectionDetails,
        topic: String,
        qos: Int,
        username: String?,
        password: String?,
        host: ReconnectHost,
    ): ExistingManagerResult {
        if (existing == null) return ExistingManagerResult.MustReplace
        if (existing.clientId == clientId && previousDetails == newDetails) {
            existing.subscribe(topic, qos)
            if (existing.isConnected()) {
                host.stopConnectTimer()
                host.updateStatus(connected = true)
            } else {
                host.startConnectTimer()
                if (!existing.isConnectingNow()) {
                    existing.connect(username, password)
                }
            }
            return ExistingManagerResult.Reattached
        }
        host.detachBroker()
        return ExistingManagerResult.MustReplace
    }

    interface ReconnectHost {
        /** Stops broker-specific connect timeout watchdog. */
        fun stopConnectTimer()
        /** Starts broker-specific connect timeout watchdog. */
        fun startConnectTimer()
        /** Publishes broker connection status to aggregate state. */
        fun updateStatus(connected: Boolean)
        /** Detaches and cleans up broker manager for reconnection replacement. */
        fun detachBroker()
    }
}
